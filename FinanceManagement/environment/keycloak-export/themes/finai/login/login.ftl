<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=false; section>
    <#if section = "header">
        Welcome to FinAI
    <#elseif section = "form">
    <div id="kc-form">
      <div id="kc-form-wrapper">
        <#if realm.password>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                <#if !usernameHidden??>
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="username" class="${properties.kcLabelClass!}">
                            <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                        </label>

                        <input tabindex="1" id="username" class="${properties.kcInputClass!}" name="username" value="${(login.username!'')}"  type="text" autofocus autocomplete="off"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>"
                        />

                        <#if messagesPerField.existsError('username','password')>
                            <span id="input-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                            </span>
                        </#if>

                    </div>
                </#if>

                <div class="${properties.kcFormGroupClass!}">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                        <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>
                        <#if realm.resetPasswordAllowed>
                            <span style="font-size: 0.875rem;"><a tabindex="5" href="${url.loginResetCredentialsUrl}" style="color: rgba(167, 139, 250, 0.8);">${msg("doForgotPassword")}</a></span>
                        </#if>
                    </div>
                    <input tabindex="2" id="password" class="${properties.kcInputClass!}" name="password" type="password" autocomplete="off"
                           aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" placeholder="${msg("password")}"
                    />
                </div>

                <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                    <div id="kc-form-options">
                        <#if realm.rememberMe && !usernameHidden??>
                            <div class="checkbox">
                                <label>
                                    <#if login.rememberMe??>
                                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" checked> ${msg("rememberMe")}
                                    <#else>
                                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox"> ${msg("rememberMe")}
                                    </#if>
                                </label>
                            </div>
                        </#if>
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                    <input tabindex="4" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                </div>
            </form>
        </#if>
        </div>

        <#if realm.password && social.providers??>
            <div id="kc-social-providers" class="${properties.kcFormSocialAccountSectionClass!}">
                <ul class="${properties.kcFormSocialAccountListClass!}">
                    <#list social.providers as p>
                        <li>
                            <a id="social-${p.alias}" class="zocial" href="${p.loginUrl}" title="${p.displayName}">
                                <#if p.alias == "github">
                                    <i class="fab fa-github"></i>
                                <#elseif p.alias == "facebook">
                                    <i class="fab fa-facebook"></i>
                                <#elseif p.alias == "google">
                                    <i class="fab fa-google"></i>
                                <#elseif p.iconClasses?has_content>
                                    <i class="${p.iconClasses!}" aria-hidden="true"></i>
                                <#else>
                                    <i class="fas fa-sign-in-alt"></i>
                                </#if>
                            </a>
                        </li>
                    </#list>
                </ul>
            </div>
        <#elseif realm.password>
            <#-- Fallback social providers for demo purposes -->
            <div id="kc-social-providers">
                <ul>
                    <li><a class="zocial" href="#" onclick="return false;" style="cursor: not-allowed; opacity: 0.5;" title="GitHub Login (Not configured)"><i class="fab fa-github"></i></a></li>
                    <li><a class="zocial" href="#" onclick="return false;" style="cursor: not-allowed; opacity: 0.5;" title="Facebook Login (Not configured)"><i class="fab fa-facebook"></i></a></li>
                    <li><a class="zocial" href="#" onclick="return false;" style="cursor: not-allowed; opacity: 0.5;" title="Email Login (Not configured)"><i class="fas fa-envelope"></i></a></li>
                </ul>
            </div>
        </#if>
      </div>
    </#if>

</@layout.registrationLayout>
