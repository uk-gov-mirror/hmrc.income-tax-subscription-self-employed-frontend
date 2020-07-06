/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers

import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.{InvalidJson, UnexpectedStatusFailure}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.PostSelfEmploymentsHttpParser.PostSelfEmploymentsSuccessResponse
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.mocks.MockIncomeTaxSubscriptionConnector
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.BusinessAccountingMethodForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.AccountingMethodModel
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.TestModels._

class BusinessAccountingMethodControllerSpec extends ControllerBaseSpec
  with MockIncomeTaxSubscriptionConnector {

  override val controllerName: String = "BusinessAccountingMethodController"
  override val authorisedRoutes: Map[String, Action[AnyContent]] = Map(
    "show" -> TestBusinessAccountingMethodController$.show(),
    "submit" -> TestBusinessAccountingMethodController$.submit()
  )

  object TestBusinessAccountingMethodController$ extends BusinessAccountingMethodController(
    mockMessagesControllerComponents,
    mockIncomeTaxSubscriptionConnector,
    mockAuthService
  )

  def modelToFormData(accountingMethodModel: AccountingMethodModel): Seq[(String, String)] = {
    BusinessAccountingMethodForm.businessAccountingMethodForm.fill(accountingMethodModel).data.toSeq
  }

  "Show" should {

    "return ok (200)" when {
      "the connector returns data" in {
        mockAuthSuccess()
        mockGetSelfEmployments(BusinessAccountingMethodController.businessAccountingMethodKey)(
          Right(Some(testAccountingMethodModel))
        )
        val result = TestBusinessAccountingMethodController$.show()(FakeRequest())
        status(result) mustBe OK
        contentType(result) mustBe Some("text/html")
      }
      "the connector returns no data" in {
        mockAuthSuccess()
        mockGetSelfEmployments(BusinessAccountingMethodController.businessAccountingMethodKey)(Right(None))
        val result = TestBusinessAccountingMethodController$.show()(FakeRequest())
        status(result) mustBe OK
        contentType(result) mustBe Some("text/html")
      }
    }
    "Throw an internal exception" when {
      "there is an unexpected status failure" in {
        mockAuthSuccess()
        mockGetSelfEmployments(BusinessAccountingMethodController.businessAccountingMethodKey)(Left(UnexpectedStatusFailure(INTERNAL_SERVER_ERROR)))
        val response = intercept[InternalServerException](await(TestBusinessAccountingMethodController$.show()(FakeRequest())))
        response.message mustBe("[BusinessAccountingMethodController][show] - Unexpected status: 500")
      }

      "there is an invalid Json" in {
        mockAuthSuccess()
        mockGetSelfEmployments(BusinessAccountingMethodController.businessAccountingMethodKey)(Left(InvalidJson))
        val response = intercept[InternalServerException](await(TestBusinessAccountingMethodController$.show()(FakeRequest())))
        response.message mustBe("[BusinessAccountingMethodController][show] - Invalid Json")
      }
    }

  }

  "Submit" should {

    "return 303, SEE_OTHER)" when {
      "the user submits valid data" in {
        mockAuthSuccess()
        mockSaveSelfEmployments(BusinessAccountingMethodController.businessAccountingMethodKey,
          testAccountingMethodModel)(Right(PostSelfEmploymentsSuccessResponse))
        val result = TestBusinessAccountingMethodController$.submit()(
          FakeRequest().withFormUrlEncodedBody(modelToFormData(testAccountingMethodModel): _*)
        )
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe
          Some(uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.routes.BusinessAccountingMethodController.show().url)
      }
    }
    "return 400, SEE_OTHER)" when {
      "the user submits invalid data" in {
        mockAuthSuccess()
        mockSaveSelfEmployments(BusinessAccountingMethodController.businessAccountingMethodKey,
          "invalid")(Right(PostSelfEmploymentsSuccessResponse))
        val result = TestBusinessAccountingMethodController$.submit()(FakeRequest())
        status(result) mustBe BAD_REQUEST
        contentType(result) mustBe Some("text/html")
      }
    }
  }

  "The back url" should {
    "return a url for the business trade name page" in {
      mockAuthSuccess()
      TestBusinessAccountingMethodController$.backUrl() mustBe routes.BusinessTradeNameController.show().url
    }
  }
  authorisationTests()

}
