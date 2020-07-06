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
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.UnexpectedStatusFailure
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.PostSelfEmploymentsHttpParser
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.PostSelfEmploymentsHttpParser._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.mocks.MockIncomeTaxSubscriptionConnector
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.{BusinessNameForm, BusinessStartDateForm}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{BusinessNameModel, BusinessStartDate}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.TestModels._

import scala.concurrent.Future

class BusinessNameControllerSpec extends ControllerBaseSpec
  with MockIncomeTaxSubscriptionConnector {
  override val controllerName: String = "BusinessNameController"
  override val authorisedRoutes: Map[String, Action[AnyContent]] = Map(
    "show" -> TestBusinessNameController$.show(),
    "submit" -> TestBusinessNameController$.submit()
  )

  object TestBusinessNameController$ extends BusinessNameController(
    mockMessagesControllerComponents,
    mockIncomeTaxSubscriptionConnector,
    mockAuthService
  )

  def modelToFormData(businessNameModel: BusinessNameModel): Seq[(String, String)] = {
    BusinessNameForm.businessNameValidationForm.fill(businessNameModel).data.toSeq
  }

  "show" should {
    "return ok (200)" when {
      "the connector returns data" in {
        mockAuthSuccess()
        mockGetSelfEmployments(BusinessNameController.businessNameKey)(
          Right(Some(BusinessNameModel("Business")))
        )
        val result = TestBusinessNameController$.show()(FakeRequest())
        status(result) mustBe OK
        contentType(result) mustBe Some("text/html")
      }
      "the connector returns no data" in {
        mockAuthSuccess()
        mockGetSelfEmployments(BusinessNameController.businessNameKey)(
          Right(Some(BusinessNameModel("")))
        )
        val result = TestBusinessNameController$.show()(FakeRequest())
        status(result) mustBe OK
        contentType(result) mustBe Some("text/html")
      }
      "Throw an internal exception error" when {
        "the connector returns an error" in {
          mockAuthSuccess()
          mockGetSelfEmployments(BusinessNameController.businessNameKey)(
            Left(UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
          )
          intercept[InternalServerException](await(TestBusinessNameController$.show()(FakeRequest())))
        }
      }
    }
  }

  "submit" should {
    "return 303, SEE_OTHER" when {
      "the connector has data to save" in {
        mockAuthSuccess()
        mockSaveSelfEmployments(BusinessNameController.businessNameKey, mockBusinessNameModel)(Right(PostSelfEmploymentsSuccessResponse))
        val result = TestBusinessNameController$.submit()(FakeRequest().withFormUrlEncodedBody(modelToFormData(mockBusinessNameModel): _*)
        )
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.BusinessTradeNameController.show().url)

      }

      "return a 400, BAD_REQUEST" when {
        "the connector has no data to save" in {
          mockAuthSuccess()
          mockSaveSelfEmployments(BusinessNameController.businessNameKey, mockBusinessNameModel)(Right(PostSelfEmploymentsSuccessResponse))
          val result = TestBusinessNameController$.submit()(FakeRequest())
          status(result) mustBe BAD_REQUEST
          contentType(result) mustBe Some("text/html")
        }
      }
    }
  }
  authorisationTests()
}
