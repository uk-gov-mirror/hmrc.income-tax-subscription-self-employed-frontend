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

import connectors.stubs.IncomeTaxSubscriptionConnectorStub.{stubDeleteSubscriptionData, stubGetSubscriptionData, stubSaveSubscriptionData}
import helpers.ComponentSpecBase
import helpers.IntegrationTestConstants.{id, individualGlobalCYAUri, soleTraderBusinesses, yourIncomeSources}
import helpers.servicemocks.AuthStub.stubAuthSuccess
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.libs.json.Json
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.SelfEmploymentDataKeys.{incomeSourcesComplete, soleTraderBusinessesKey}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.SoleTraderBusinesses

class SelfEmployedCYAControllerISpec extends ComponentSpecBase with FeatureSwitching {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val incompleteSoleTraderBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(
    accountingMethod = None
  )

  val completeSoleTraderBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(
    businesses = soleTraderBusinesses.businesses.map(_.copy(confirmed = true))
  )

  "GET /report-quarterly/income-and-expenses/sign-up/self-employments/details/business-check-your-answers" should {
    "return OK" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))

      When("GET /details/business-check-your-answers is called")
      val res = getBusinessCheckYourAnswers(id, isEditMode = false)

      Then("should return an OK with the SelfEmployedCYA page")
      res must have(
        httpStatus(OK),
        pageTitle("Check your answers - sole trader business" + titleSuffix)
      )
    }

    "return INTERNAL_SERVER_ERROR" when {
      "the sole trader businesses can't be retrieved" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(INTERNAL_SERVER_ERROR)

        When("GET /details/business-check-your-answers is called")
        val res = getBusinessCheckYourAnswers(id, isEditMode = false)

        Then("Should return INTERNAL_SERVER_ERROR")
        res must have(
          httpStatus(INTERNAL_SERVER_ERROR)
        )
      }
    }
  }

  "POST /report-quarterly/income-and-expenses/sign-up/self-employments/details/business-check-your-answers" should {
    "redirect to the your income source page" when {
      "the user submits valid full data" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))
        stubSaveSubscriptionData(reference, soleTraderBusinessesKey, Json.toJson(completeSoleTraderBusinesses))(OK)
        stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

        When("GET /details/business-check-your-answers is called")
        val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = false)

        Then("Should return a SEE_OTHER with a redirect location of task list page")
        res must have(
          httpStatus(SEE_OTHER),
          redirectURI(yourIncomeSources)
        )
      }

      "redirect to Global CYA page" when {
        "isGlobalEdit is true and the user submits valid full data" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))
          stubSaveSubscriptionData(reference, soleTraderBusinessesKey, Json.toJson(completeSoleTraderBusinesses))(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When("POST /details/business-check-your-answers is called")
          val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = true)

          Then("Should return a SEE_OTHER with a redirect location of Global CYA page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(individualGlobalCYAUri)
          )
        }
      }

      "the user submits valid incomplete data" in {
        disable(RemoveAccountingMethod)
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(incompleteSoleTraderBusinesses))

        When("GET /details/business-check-your-answers is called")
        val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = false)

        Then("Should return a SEE_OTHER with a redirect location of self-employed CYA page")
        res must have(
          httpStatus(SEE_OTHER),
          redirectURI(yourIncomeSources)
        )
      }
    }

    "return INTERNAL_SERVER_ERROR" when {
      "the sole trader businesses can't be retrieved" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(INTERNAL_SERVER_ERROR)

        When("GET /details/business-check-your-answers is called")
        val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = false)

        Then("Should return INTERNAL_SERVER_ERROR")
        res must have(
          httpStatus(INTERNAL_SERVER_ERROR)
        )
      }

      "self employment data cannot be saved" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))
        stubSaveSubscriptionData(reference, soleTraderBusinessesKey, Json.toJson(completeSoleTraderBusinesses))(INTERNAL_SERVER_ERROR)

        When("GET /details/business-check-your-answers is called")
        val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = false)

        Then("Should return INTERNAL_SERVER_ERROR")
        res must have(
          httpStatus(INTERNAL_SERVER_ERROR)
        )
      }
    }

    "remove accounting method feature switch is enabled" should {
      "redirect to the your income source page" when {
        "the user submits valid full data without accounting method" in {
          enable(RemoveAccountingMethod)
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))
          stubSaveSubscriptionData(reference, soleTraderBusinessesKey, Json.toJson(completeSoleTraderBusinesses))(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When("GET /details/business-check-your-answers is called")
          val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = false)

          Then("Should return a SEE_OTHER with a redirect location of task list page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(yourIncomeSources)
          )
        }

        "redirect to Global CYA page" when {
          "isGlobalEdit is true and the user submits valid full data without accounting method" in {
            enable(RemoveAccountingMethod)
            Given("I setup the Wiremock stubs")
            stubAuthSuccess()
            stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(soleTraderBusinesses))
            stubSaveSubscriptionData(reference, soleTraderBusinessesKey, Json.toJson(completeSoleTraderBusinesses))(OK)
            stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

            When("POST /details/business-check-your-answers is called")
            val res = submitBusinessCheckYourAnswers(id, isGlobalEdit = true)

            Then("Should return a SEE_OTHER with a redirect location of Global CYA page")
            res must have(
              httpStatus(SEE_OTHER),
              redirectURI(individualGlobalCYAUri)
            )
          }
        }
      }
    }
  }
}
