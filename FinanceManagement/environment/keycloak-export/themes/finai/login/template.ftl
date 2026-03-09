<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" class="${properties.kcHtmlClass!}">

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    
    <!-- Font Awesome for social icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" integrity="sha512-iecdLmaskl7CVkqkXNQ/ZH/XLlvWZOJyj7Yy7tcenmpD1ypASozpmT/E0iPtmFIB46ZmdtAc9eNBvH0H/ZpiBw==" crossorigin="anonymous" referrerpolicy="no-referrer" />
</head>

<body class="${properties.kcBodyClass!}">
    <div id="kc-page-wrapper" style="display: flex; min-height: 100vh; width: 100%;">
        
        <!-- Left Panel - Auth Form -->
        <div style="width: 100%; display: flex; align-items: center; justify-content: center; position: relative; z-index: 10;">
            <div id="kc-content-wrapper">
                
                <#-- App-level wrapper -->
                <div id="kc-header" class="${properties.kcHeaderClass!}">
                    <div id="kc-header-wrapper">
                        <#nested "header">
                        <p class="subtitle">${msg("loginSubTitle")}</p>
                        
                        <#-- Tab Navigation for Login/Register -->
                        <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                            <div class="kc-tab-nav">
                                <a href="${url.loginUrl}" class="<#if !(url.loginRestartFlowUrl?has_content || url.registrationUrl?has_content)>active</#if>">Login</a>
                                <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
                                    <a href="${url.registrationUrl}">Register</a>
                                </#if>
                            </div>
                        </#if>
                    </div>
                </div>

                <#-- Display alerts/messages -->
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="alert alert-${message.type}">
                        <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                        <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                        <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                        <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                        <span class="kc-feedback-text">${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <#-- Main form content -->
                <div id="kc-content">
                    <div id="kc-form">
                        <div id="kc-form-wrapper">
                            <#nested "form">
                        </div>
                    </div>
                </div>

                <#-- Info section (if needed) -->
                <#if displayInfo && !(realm.password && social.providers??)>
                    <div id="kc-info" class="${properties.kcSignUpClass!}">
                        <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                            <#nested "info">
                        </div>
                    </div>
                </#if>

                <#-- Locale selector (if multiple languages) -->
                <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
                    <div id="kc-locale">
                        <div id="kc-locale-wrapper" class="${properties.kcLocaleWrapperClass!}">
                            <div class="kc-dropdown" id="kc-locale-dropdown">
                                <a href="#" id="kc-current-locale-link">${locale.current}</a>
                                <ul>
                                    <#list locale.supported as l>
                                        <li class="kc-dropdown-item"><a href="${l.url}">${l.label}</a></li>
                                    </#list>
                                </ul>
                            </div>
                        </div>
                    </div>
                </#if>
            </div>
        </div>

        <!-- Right Panel - AI Visual Background (Hidden on Mobile) -->
        <div style="display: none; position: fixed; right: 0; top: 0; bottom: 0; width: 45%; background: #000; align-items: center; justify-content: center; padding: 3rem; overflow: hidden; z-index: 5;">
            <#-- Background Image with Overlay -->
            <div style="position: absolute; inset: 0; z-index: 0;">
                <img src="${url.resourcesPath}/img/ai-finance-bg.png" alt="AI Finance Background" style="width: 100%; height: 100%; object-fit: cover; opacity: 0.6;" />
                <div style="position: absolute; inset: 0; background: linear-gradient(to top, var(--background) 0%, transparent 50%, transparent 100%);"></div>
                <div style="position: absolute; inset: 0; background: linear-gradient(to right, var(--background) 0%, transparent 30%, transparent 100%);"></div>
            </div>

            <#-- Floating AI Feature Cards -->
            <div style="position: relative; z-index: 10; width: 100%; max-width: 32rem; margin: 0 auto;">
                <div class="glass-panel" style="padding: 1.5rem; border-radius: 1rem; margin-bottom: 1.5rem; animation: slideInRight 0.6s ease 0.2s both;">
                    <div style="display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem;">
                        <div style="padding: 0.75rem; border-radius: 9999px; background: linear-gradient(135deg, rgba(20, 184, 166, 0.25) 0%, rgba(59, 130, 246, 0.2) 100%); color: #14b8a6; box-shadow: 0 0 24px rgba(20, 184, 166, 0.3);">
                            <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1H2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2z"/>
                            </svg>
                        </div>
                        <div>
                            <h3 style="font-weight: 600; color: #fff; margin: 0;">Smart Analysis</h3>
                            <p style="font-size: 0.875rem; color: rgba(255, 255, 255, 0.6); margin: 0;">AI-driven spending insights</p>
                        </div>
                    </div>
                    <div style="height: 0.5rem; background: rgba(20, 184, 166, 0.2); border-radius: 9999px; overflow: hidden;">
                        <div style="height: 100%; width: 75%; background: linear-gradient(90deg, #14b8a6 0%, #3b82f6 100%);"></div>
                    </div>
                </div>

                <div class="glass-panel" style="padding: 1.5rem; border-radius: 1rem; margin-bottom: 1.5rem; margin-left: 3rem; animation: slideInRight 0.6s ease 0.4s both;">
                    <div style="display: flex; align-items: center; gap: 1rem;">
                        <div style="padding: 0.75rem; border-radius: 9999px; background: linear-gradient(135deg, rgba(59, 130, 246, 0.25) 0%, rgba(20, 184, 166, 0.2) 100%); color: #3b82f6; box-shadow: 0 0 24px rgba(59, 130, 246, 0.3);">
                            <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                                <path d="m9 12 2 2 4-4"/>
                            </svg>
                        </div>
                        <div>
                            <h3 style="font-weight: 600; color: #fff; margin: 0;">Secure Access</h3>
                            <p style="font-size: 0.875rem; color: rgba(255, 255, 255, 0.6); margin: 0;">Biometric & Encrypted</p>
                        </div>
                    </div>
                </div>

                <div class="glass-panel" style="padding: 1.5rem; border-radius: 1rem; animation: slideInRight 0.6s ease 0.6s both;">
                    <div style="display: flex; align-items: center; gap: 1rem;">
                        <div style="padding: 0.75rem; border-radius: 9999px; background: linear-gradient(135deg, rgba(6, 182, 212, 0.25) 0%, rgba(20, 184, 166, 0.2) 100%); color: #06b6d4; box-shadow: 0 0 24px rgba(6, 182, 212, 0.3);">
                            <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
                                <polyline points="16 7 22 7 22 13"/>
                            </svg>
                        </div>
                        <div>
                            <h3 style="font-weight: 600; color: #fff; margin: 0;">Wealth Growth</h3>
                            <p style="font-size: 0.875rem; color: rgba(255, 255, 255, 0.6); margin: 0;">+12.4% portfolio increase</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <style>
        @keyframes slideInRight {
            from {
                opacity: 0;
                transform: translateX(50px);
            }
            to {
                opacity: 1;
                transform: translateX(0);
            }
        }

        /* Show right panel on large screens */
        @media (min-width: 1024px) {
            #kc-page-wrapper > div:first-child {
                width: 55% !important;
                position: relative;
                z-index: 10;
            }
            #kc-page-wrapper > div:last-child {
                display: flex !important;
                position: fixed;
                right: 0;
                top: 0;
                bottom: 0;
                width: 45% !important;
                z-index: 5;
            }
        }

        /* Locale dropdown styles */
        #kc-locale-dropdown {
            position: relative;
        }
        #kc-locale-dropdown ul {
            display: none;
            position: absolute;
            background: var(--secondary);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            padding: 0.5rem 0;
            list-style: none;
            margin: 0.25rem 0 0 0;
            min-width: 100px;
        }
        #kc-locale-dropdown:hover ul {
            display: block;
        }
        #kc-locale-dropdown ul li a {
            display: block;
            padding: 0.5rem 1rem;
            color: var(--foreground);
            text-decoration: none;
        }
        #kc-locale-dropdown ul li a:hover {
            background: rgba(255, 255, 255, 0.05);
        }
    </style>
</body>
</html>
</#macro>
