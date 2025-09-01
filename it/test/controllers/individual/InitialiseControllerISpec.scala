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

package controllers.individual

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

  "GET /report-quarterly/income-and-expenses/sign-up/self-employments/details" when {

    "redirect to full income source page when there is already at least 1 business with accounting method" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))

      When("GET /details is called")
      val res = getInitialise

      Then("I should redirect to full income source page")
      res must have(
        httpStatus(SEE_OTHER),
        redirectURI("/details/sole-trader-business"))
    }

    "redirect to accounting method page when a business exists but no accounting method" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses.copy(accountingMethod = None)))

      When("GET /details is called")
      val res = getInitialise

      Then("I should redirect to accounting method page")
      res must have(
        httpStatus(SEE_OTHER),
        redirectURI("/details/business-accounting-method"))
    }

    "redirect to accounting method page when there are no previous businesses" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubGetSubscriptionData(reference, soleTraderBusinessesKey)(NO_CONTENT)

      When("GET /details is called")
      val res = getInitialise

      Then("I should redirect to accounting method page")
      res must have(
        httpStatus(SEE_OTHER),
        redirectURI("/details/business-accounting-method"))
    }

    "return INTERNAL_SERVER_ERROR when failed to fetch sole trader businesses" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubGetSubscriptionData(reference, soleTraderBusinessesKey)(INTERNAL_SERVER_ERROR)

      When("GET /details is called")
      val res = getInitialise

      Then("I should throw internal server error")
      res must have(
        httpStatus(INTERNAL_SERVER_ERROR))
    }

    "the remove accounting method feature switch is enabled" should {
      "redirect to full income source page when there is already at least 1 business without accounting method" in {
        enable(RemoveAccountingMethod)
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses.copy(accountingMethod = None)))

        When("GET /details is called")
        val res = getInitialise

        Then("I should redirect to full income source page")
        res must have(
          httpStatus(SEE_OTHER),
          redirectURI("/details/sole-trader-business")
        )
      }

      "redirect to full income source page if there are no previous businesses" in {
        enable(RemoveAccountingMethod)
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(NO_CONTENT)

        When("GET /details is called")
        val res = getInitialise

        Then("I should redirect to full income source page")
        res must have(
          httpStatus(SEE_OTHER),
          redirectURI("/details/sole-trader-business")
        )
      }
    }
  }
}
