/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.individual

import org.mockito.Mockito.when
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.mvc.{Action, AnyContent}
import play.api.test.Helpers._
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.UnexpectedStatusFailure
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.ControllerBaseSpec
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{Cash, SoleTraderBusiness, SoleTraderBusinesses}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.mocks.{MockMultipleSelfEmploymentsService, MockSessionDataService}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.UUIDGenerator

class InitialiseControllerSpec extends ControllerBaseSpec with MockMultipleSelfEmploymentsService with MockSessionDataService with FeatureSwitching {

  override val controllerName: String = "InitialiseController"
  override val authorisedRoutes: Map[String, Action[AnyContent]] = Map()

  val mockUuid: UUIDGenerator = mock[UUIDGenerator]

  when(mockUuid.generateId).thenReturn("testId")

  object TestInitialiseController extends InitialiseController(
    mockMessagesControllerComponents,
    mockAuthService,
    mockMultipleSelfEmploymentsService,
    mockUuid
  )(
    appConfig,
    mockSessionDataService
  )

  "initialise" when {
    s"return $SEE_OTHER and redirect to Full Income Source page when a business with accounting method already exists" in {
      mockAuthSuccess()
      mockFetchSoleTraderBusinesses(Right(Some(SoleTraderBusinesses(
        businesses = Seq(SoleTraderBusiness(id = "firstId")),
        accountingMethod = Some(Cash)))))

      val result = TestInitialiseController.initialise(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.FullIncomeSourceController.show(id = "testId").url)
    }

    s"return $SEE_OTHER and redirect to Accounting Method page when a business exists without accounting method" in {
      mockAuthSuccess()
      mockFetchSoleTraderBusinesses(Right(Some(SoleTraderBusinesses(
        businesses = Seq(SoleTraderBusiness(id = "firstId")),
        accountingMethod = None))))

      val result = TestInitialiseController.initialise(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.BusinessAccountingMethodController.show(id = "testId").url)
    }

    s"return $SEE_OTHER and redirect to Accounting Method page when adding the first business" in {
      mockAuthSuccess()
      mockFetchSoleTraderBusinesses(Right(Some(SoleTraderBusinesses(Seq.empty))))

      val result = TestInitialiseController.initialise(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.BusinessAccountingMethodController.show(id = "testId").url)
    }

    s"return $INTERNAL_SERVER_ERROR when failed to fetch sole trader businesses" in {
      mockAuthSuccess()
      mockFetchSoleTraderBusinesses(Left(UnexpectedStatusFailure(INTERNAL_SERVER_ERROR)))

      intercept[InternalServerException](await(TestInitialiseController.initialise(fakeRequest)))
        .message mustBe "[InitialiseController][initialise] - Failure fetching sole trader businesses"
    }

    "when remove accounting method feature switch is enabled" should {
      s"return $SEE_OTHER and redirect to Full Income Source page when a business already exists without accounting method" in {
        enable(RemoveAccountingMethod)
        mockAuthSuccess()
        mockFetchSoleTraderBusinesses(Right(Some(SoleTraderBusinesses(
          businesses = Seq(SoleTraderBusiness(id = "firstId"))))))

        val result = TestInitialiseController.initialise(fakeRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.FullIncomeSourceController.show(id = "testId").url)
      }

      s"return $SEE_OTHER and redirect to Full Income Source page when adding a business" in {
        enable(RemoveAccountingMethod)
        mockAuthSuccess()
        mockFetchSoleTraderBusinesses(Right(Some(SoleTraderBusinesses(
          businesses = Seq.empty,
          accountingMethod = None))))

        val result = TestInitialiseController.initialise(fakeRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.FullIncomeSourceController.show(id = "testId").url)
      }
    }
  }

  authorisationTests()

}
