/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.agent

import connectors.stubs.IncomeTaxSubscriptionConnectorStub.stubGetSubscriptionData
import helpers.ComponentSpecBase
import helpers.IntegrationTestConstants.soleTraderBusinesses
import helpers.servicemocks.AuthStub.stubAuthSuccess
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.SelfEmploymentDataKeys.soleTraderBusinessesKey
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching

class InitialiseControllerISpec extends ComponentSpecBase with FeatureSwitching {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  override def beforeEach(): Unit = {
    super.beforeEach()
    disable(RemoveAccountingMethod)
  }

  "GET /report-quarterly/income-and-expenses/sign-up/self-employments/client/details" when {
    "the remove accounting method feature switch is enabled" should {
      "redirect to the full sole trader income source page" in {
        enable(RemoveAccountingMethod)

        Given("I setup the Wiremock stubs")
        stubAuthSuccess()

        When("GET /details is called")
        val res = getClientInitialise

        Then("should redirect to the full income source page page")
        res must have(
          httpStatus(SEE_OTHER),
          redirectURI("/client/details/sole-trader-business")
        )
      }
    }
    "the remove accounting method feature switch is disabled" should {
      "redirect to the first sole trader income source page" when {
        "there are no sole trader businesses currently" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(NO_CONTENT)

          When("GET /details is called")
          val res = getClientInitialise

          Then("should redirect to the intitial sole trader business page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI("/client/details/initial-sole-trader-business")
          )
        }
      }
      "redirect to the next sole trader income source page" when {
        "sole trader businesses exist" in {

          Given("I setup the Wiremock stubs")
          stubAuthSuccess()

          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))

          When("GET /details is called")
          val res = getClientInitialise

          Then("should redirect to the subsequent name page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI("/client/details/subsequent-sole-trader-business")
          )
        }
      }
    }
  }
}
