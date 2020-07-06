/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import connectors.stubs.IncomeTaxSubscriptionConnectorStub._
import helpers.ComponentSpecBase
import helpers.IntegrationTestConstants._
import helpers.servicemocks.AuthStub._
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.BusinessTradeNameController

class BusinessTradeNameControllerISpec extends ComponentSpecBase {


  "GET /report-quarterly/income-and-expenses/sign-up/self-employments/details/business-trade" when {

    "the Connector receives no content" should {
      "return the page with no prepopulated fields" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSelfEmployments(BusinessTradeNameController.businessTradeNameKey)(NO_CONTENT)

        When("GET /details/business-trade is called")
        val res = getBusinessTradeName()

        Then("should return an OK with the BusinessTradeNamePage")
        res must have(
          httpStatus(OK),
          pageTitle("What is the trade of your business?"),
          textField("businessTradeName", "")
        )
      }
    }

    "Connector returns a previously filled in Business Trade Name" should {
      "show the current business trade name page with name values entered" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSelfEmployments(BusinessTradeNameController.businessTradeNameKey)(OK, Json.toJson(testValidBusinessTradeNameModel))

        When("GET /details/business-trade is called")
        val res = getBusinessTradeName()

        Then("should return an OK with the BusinessTradeNamePage")
        res must have(
          httpStatus(OK),
          pageTitle("What is the trade of your business?"),
          textField("businessTradeName", testValidBusinessTradeName)
        )
      }
    }

  }

  "POST /report-quarterly/income-and-expenses/sign-up/self-employments/details/business-trade" when {
    "the form data is valid and connector stores it successfully" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubSaveSelfEmployments(BusinessTradeNameController.businessTradeNameKey, Json.toJson(testValidBusinessTradeNameModel))(OK)

      When("POST /details/business-trade is called")
      val res = submitBusinessTradeName(Some(testValidBusinessTradeNameModel))


      Then("Should return a SEE_OTHER with a redirect location of accounting period dates")
      res must have(
        httpStatus(SEE_OTHER),
        redirectURI(BusinessAccountingMethodUri)
      )
    }

    "the form data is invalid and connector stores it unsuccessfully" in {
      Given("I setup the Wiremock stubs")
      stubAuthSuccess()
      stubSaveSelfEmployments(BusinessTradeNameController.businessTradeNameKey, Json.toJson(testInvalidBusinessTradeNameModel))(OK)

      When("POST /details/business-trade is called")
      val res = submitBusinessTradeName(Some(testInvalidBusinessTradeNameModel))


      Then("Should return a BAD_REQUEST and THE FORM With errors")
      res must have(
        httpStatus(BAD_REQUEST),
        pageTitle("Error: What is the trade of your business?")
      )
    }

  }
}
