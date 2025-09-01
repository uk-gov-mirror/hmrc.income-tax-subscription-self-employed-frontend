/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(servicesConfig: ServicesConfig, val config: Configuration) {

  private lazy val contactHost: String = servicesConfig.getString("contact-frontend.host")

  lazy val protectedMicroServiceUrl: String = servicesConfig.baseUrl("income-tax-subscription")
  lazy val incomeTaxSubscriptionFrontendBaseUrl: String = servicesConfig.getString("income-tax-subscription-frontend.url")
  lazy val incomeTaxSubscriptionSelfEmployedFrontendBaseUrl: String = servicesConfig.getString("income-tax-subscription-self-employed-frontend.url")

  //  Individual routes
  lazy val yourIncomeSourcesUrl: String = incomeTaxSubscriptionFrontendBaseUrl + "/details/your-income-source"
  lazy val subscriptionFrontendProgressSavedUrl: String = incomeTaxSubscriptionFrontendBaseUrl + "/business/progress-saved"
  lazy val individualGlobalCYAUrl: String = incomeTaxSubscriptionFrontendBaseUrl + "/final-check-your-answers"

  //  Agent routes
  lazy val subscriptionFrontendClientProgressSavedUrl: String = incomeTaxSubscriptionFrontendBaseUrl + "/client/business/progress-saved"
  lazy val clientYourIncomeSourcesUrl: String = incomeTaxSubscriptionFrontendBaseUrl + "/client/your-income-source"
  lazy val globalCYAUrl: String = incomeTaxSubscriptionFrontendBaseUrl + "/client/final-check-your-answers"

  private lazy val ggUrl: String = servicesConfig.getString(s"government-gateway.url")
  lazy val addressLookupUrl: String = servicesConfig.baseUrl("address-lookup-frontend")
  lazy val stubAddressLookupUrl: String = servicesConfig.baseUrl("income-tax-subscription-stubs")
  lazy val timeoutWarningInSeconds: String = servicesConfig.getString("session-timeout.warning")
  lazy val timeoutInSeconds: String = servicesConfig.getString("session-timeout.seconds")

  private val contactFormServiceIdentifier = "MTDIT"

  val feedbackFrontendRedirectUrl: String = servicesConfig.getString("feedback-frontend.url")
  val feedbackFrontendRedirectUrlAgent: String = servicesConfig.getString("feedback-frontend.agent.url")
  val urBannerUrl: String = servicesConfig.getString("urBannerUrl.url")

  def ggSignOutUrl(redirectionUrl: String = incomeTaxSubscriptionFrontendBaseUrl): String =
    s"$ggUrl/bas-gateway/sign-out-without-state?continue=$redirectionUrl"

  def betaFeedbackUnauthenticatedUrl: String = s"$contactHost/contact/beta-feedback-unauthenticated?service=$contactFormServiceIdentifier"

  private val govukGuidanceLink: String = servicesConfig.getString("govuk-guidance.url")
  val govukGuidanceITSASignUpIndivLink: String = s"$govukGuidanceLink/sign-up-your-business-for-making-tax-digital-for-income-tax"
  val govukGuidanceITSASignUpAgentLink: String = s"$govukGuidanceLink/sign-up-your-client-for-making-tax-digital-for-income-tax"

}
