package com.finance.aiservice.service;

import com.finance.aiservice.engine.AiReasoningEngine;
import com.finance.aiservice.engine.AiReasoningEngine.AiEnhancement;
import com.finance.aiservice.engine.AnalysisRequest;
import com.finance.aiservice.engine.AnalysisResult;
import com.finance.aiservice.engine.RuleBasedEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Hybrid Engine Service - orchestrates Rule-Based + AI Reasoning engines.
 *
 * Architecture:
 * 1. RuleBasedEngine runs FIRST (fast, deterministic, always reliable)
 * 2. AiReasoningEngine ENHANCES the rule-based results (deeper insights, narratives)
 * 3. Results are MERGED into a single HYBRID response
 *
 * Graceful Degradation:
 * - If AI reasoning fails or times out, returns rule-based results only
 * - AI enhancement is non-blocking with configurable timeout
 * - Feature toggle via configuration: app.ai.hybrid-engine.ai-enhancement-enabled
 *
 * This ensures:
 * - Factual accuracy from rules (no hallucinations for core metrics)
 * - Deeper insights and narratives from AI reasoning
 * - System reliability even when AI is unavailable
 */
@Slf4j
@Service
public class HybridEngineService {

    private final RuleBasedEngine ruleBasedEngine;
    private final AiReasoningEngine aiReasoningEngine;
    private final ExecutorService aiExecutor;

    @Value("${app.ai.hybrid-engine.ai-enhancement-enabled:true}")
    private boolean aiEnhancementEnabled;

    @Value("${app.ai.hybrid-engine.ai-timeout-seconds:30}")
    private int aiTimeoutSeconds;

    @Value("${app.ai.hybrid-engine.fallback-to-rules-on-failure:true}")
    private boolean fallbackToRulesOnFailure;

    public HybridEngineService(RuleBasedEngine ruleBasedEngine, AiReasoningEngine aiReasoningEngine) {
        this.ruleBasedEngine = ruleBasedEngine;
        this.aiReasoningEngine = aiReasoningEngine;
        this.aiExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ai-reasoning-");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Analyze financial data using the Hybrid Engine (Rule-Based + AI Reasoning).
     *
     * @param request Analysis request
     * @return Analysis result (HYBRID if AI enhancement succeeds, RULE_BASED otherwise)
     */
    public AnalysisResult analyze(AnalysisRequest request) {
        log.info("Hybrid Engine analyzing: type={}, userId={}, aiEnabled={}",
            request.getAnalysisType(), request.getUserId(), aiEnhancementEnabled);

        // Step 1: Always run rule-based engine first (fast, reliable)
        AnalysisResult ruleBasedResult = ruleBasedEngine.analyze(request);

        if (!ruleBasedResult.isSuccess()) {
            log.warn("Rule-based engine failed, returning error result");
            return ruleBasedResult;
        }

        log.info("Rule-based analysis completed: healthScore={}, insights={}, confidence={}",
            ruleBasedResult.getHealthScore(),
            ruleBasedResult.getInsights() != null ? ruleBasedResult.getInsights().size() : 0,
            ruleBasedResult.getConfidence());

        // Step 2: If AI enhancement is disabled, return rule-based only
        if (!aiEnhancementEnabled) {
            log.info("AI enhancement disabled, returning rule-based result only");
            return ruleBasedResult;
        }

        // Step 3: Run AI reasoning with timeout
        try {
            Future<AiEnhancement> aiFuture = aiExecutor.submit(
                () -> aiReasoningEngine.enhance(ruleBasedResult, request)
            );

            AiEnhancement enhancement = aiFuture.get(aiTimeoutSeconds, TimeUnit.SECONDS);

            if (enhancement != null) {
                // Step 4: Merge results
                AnalysisResult hybridResult = mergeResults(ruleBasedResult, enhancement);
                log.info("Hybrid analysis completed: engineType=HYBRID, " +
                         "ruleInsights={}, aiInsights={}, totalInsights={}",
                    ruleBasedResult.getInsights() != null ? ruleBasedResult.getInsights().size() : 0,
                    enhancement.enhancedInsights().size(),
                    hybridResult.getInsights() != null ? hybridResult.getInsights().size() : 0);
                return hybridResult;
            }

            log.info("AI enhancement returned null, using rule-based result");
            return ruleBasedResult;

        } catch (TimeoutException e) {
            log.warn("AI reasoning timed out after {}s, falling back to rule-based result",
                aiTimeoutSeconds);
            return ruleBasedResult;

        } catch (Exception e) {
            log.warn("AI reasoning failed: {}, falling back to rule-based result", e.getMessage());
            if (!fallbackToRulesOnFailure) {
                return AnalysisResult.builder()
                    .success(false)
                    .analysisType(request.getAnalysisType())
                    .errorMessage("Hybrid Engine failed: " + e.getMessage())
                    .build();
            }
            return ruleBasedResult;
        }
    }

    /**
     * Merge rule-based results with AI enhancement into a single HYBRID result.
     */
    private AnalysisResult mergeResults(AnalysisResult ruleResult, AiEnhancement aiEnhancement) {
        // Start with all rule-based data
        List<AnalysisResult.Insight> mergedInsights = new ArrayList<>();
        if (ruleResult.getInsights() != null) {
            mergedInsights.addAll(ruleResult.getInsights());
        }

        // Add AI-discovered insights
        if (aiEnhancement.enhancedInsights() != null) {
            mergedInsights.addAll(aiEnhancement.enhancedInsights());
        }

        // Merge recommendations
        List<AnalysisResult.Recommendation> mergedRecommendations = new ArrayList<>();
        if (ruleResult.getRecommendations() != null) {
            mergedRecommendations.addAll(ruleResult.getRecommendations());
        }
        if (aiEnhancement.additionalRecommendations() != null) {
            mergedRecommendations.addAll(aiEnhancement.additionalRecommendations());
        }

        // Determine health score: use AI-adjusted score if provided and reasonable
        Integer healthScore = ruleResult.getHealthScore();
        if (aiEnhancement.adjustedHealthScore() != null) {
            int aiScore = aiEnhancement.adjustedHealthScore();
            int ruleScore = healthScore != null ? healthScore : 50;
            // Only accept AI adjustment if it's within ±15 points of rule-based score
            // This prevents AI hallucination from drastically changing the score
            if (Math.abs(aiScore - ruleScore) <= 15) {
                healthScore = aiScore;
                log.debug("AI adjusted health score: {} → {}", ruleScore, aiScore);
            } else {
                log.warn("AI health score adjustment rejected: rule={}, ai={} (diff > 15)",
                    ruleScore, aiScore);
            }
        }

        // Calculate blended confidence
        double ruleConfidence = ruleResult.getConfidence() != null ? ruleResult.getConfidence() : 0.8;
        double aiConfidence = aiEnhancement.confidence() != null ? aiEnhancement.confidence() : 0.5;
        double blendedConfidence = (ruleConfidence * 0.6) + (aiConfidence * 0.4);

        // Build hybrid insight summary
        String hybridInsight = ruleResult.getInsight();
        if (aiEnhancement.validationNotes() != null && !aiEnhancement.validationNotes().isBlank()) {
            hybridInsight += " | AI: " + aiEnhancement.validationNotes();
        }

        return AnalysisResult.builder()
            .success(true)
            .analysisType(ruleResult.getAnalysisType())
            .healthScore(healthScore)
            .insight(hybridInsight)
            .insights(mergedInsights)
            .recommendations(mergedRecommendations)
            .engineType(AnalysisResult.EngineType.HYBRID)
            .confidence(blendedConfidence)
            .aiNarrative(aiEnhancement.aiNarrative())
            .aiEnhanced(true)
            .build();
    }
}
