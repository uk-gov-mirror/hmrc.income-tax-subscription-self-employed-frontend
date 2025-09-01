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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.agent

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.data.Form
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.Helpers.{HTML, await, contentType, defaultAwaitTimeout, redirectLocation, status}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.UnexpectedStatusFailure
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.ControllerBaseSpec
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.BusinessStartDateForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.utils.FormUtil._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{DateModel, SoleTraderBusiness}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.mocks.{MockClientDetailsRetrieval, MockMultipleSelfEmploymentsService, MockSessionDataService}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.{AccountingPeriodUtil, ImplicitDateFormatter}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.agent.BusinessStartDate
import uk.gov.hmrc.play.language.LanguageUtils

import java.time.LocalDate
import scala.concurrent.Future

class BusinessStartDateControllerSpec extends ControllerBaseSpec
  with MockMultipleSelfEmploymentsService
  with MockSessionDataService
  with MockClientDetailsRetrieval
  with ImplicitDateFormatter
  with FeatureSwitching {

  val businessStartDate: BusinessStartDate = mock[BusinessStartDate]
  val id: String = "testId"

  override val languageUtils: LanguageUtils = app.injector.instanceOf[LanguageUtils]
  override val controllerName: String = "BusinessStartDateController"
  override val authorisedRoutes: Map[String, Action[AnyContent]] = Map(
    "show" -> TestBusinessStartDateController.show(id, isEditMode = false, isGlobalEdit = false),
    "submit" -> TestBusinessStartDateController.submit(id, isEditMode = false, isGlobalEdit = false)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    disable(RemoveAccountingMethod)
    reset(businessStartDate)
  }

  def mockBusinessStartDate(backUrl: String, trade: String): Unit = {
    when(businessStartDate(
      ArgumentMatchers.any(),
      ArgumentMatchers.any(),
      ArgumentMatchers.eq(backUrl),
      ArgumentMatchers.eq(clientDetails),
      ArgumentMatchers.eq(trade)
    )(any(), any())) thenReturn HtmlFormat.empty
  }

  def businessStartDateForm(fill: Option[DateModel] = None, bind: Option[Map[String, String]] = None): Form[DateModel] = {
    val filledForm = BusinessStartDateForm.businessStartDateForm(
      minStartDate = BusinessStartDateForm.minStartDate,
      maxStartDate = BusinessStartDateForm.maxStartDate,
      d => d.toLongDate()
    ).fill(fill)
    bind match {
      case Some(data) => filledForm.bind(data)
      case None => filledForm
    }
  }

  object TestBusinessStartDateController extends BusinessStartDateController(
    mockMessagesControllerComponents,
    mockClientDetailsRetrieval,
    mockMultipleSelfEmploymentsService,
    mockAuthService,
    businessStartDate
  )(
    mockSessionDataService,
    mockLanguageUtils,
    appConfig
  )

  def modelToFormData(model: DateModel): Seq[(String, String)] = {
    BusinessStartDateForm.businessStartDateForm(BusinessStartDateForm.minStartDate, BusinessStartDateForm.maxStartDate, d => d.toString).fill(model).data.toSeq
  }

  "show" must {
    "return the page content" which {
      "has a back link to the full income source page" when {
        "the remove accounting method feature switch is enabled" when {
          "there is a saved business" which {
            "has a start date and a trade" in {
              enable(RemoveAccountingMethod)

              mockAuthSuccess()
              mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id = id, startDate = Some(DateModel.dateConvert(LocalDate.now)), trade = Some("test trade")))))
              mockGetClientDetails()
              mockBusinessStartDate(
                backUrl = routes.FullIncomeSourceController.show(id).url,
                trade = "test trade"
              )

              val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

              status(result) mustBe OK
              contentType(result) mustBe Some(HTML)
            }
            "has no start date but has a trade" in {
              enable(RemoveAccountingMethod)

              mockAuthSuccess()
              mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id = id, trade = Some("test trade")))))
              mockGetClientDetails()
              mockBusinessStartDate(
                backUrl = routes.FullIncomeSourceController.show(id).url,
                trade = "test trade"
              )

              val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

              status(result) mustBe OK
              contentType(result) mustBe Some(HTML)
            }
          }
        }
      }
      "has a back link to the first income source page" when {
        "the remove accounting method feature switch is disabled" when {
          "there is a saved business" which {
            "has a start date and a trade" in {
              mockAuthSuccess()
              mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id = id, startDate = Some(DateModel.dateConvert(LocalDate.now)), trade = Some("test trade")))))
              mockGetClientDetails()
              mockBusinessStartDate(
                backUrl = routes.FirstIncomeSourceController.show(id).url,
                trade = "test trade"
              )

              val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

              status(result) mustBe OK
              contentType(result) mustBe Some(HTML)
            }
            "has no start date but has a trade" in {
              mockAuthSuccess()
              mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id = id, trade = Some("test trade")))))
              mockGetClientDetails()
              mockBusinessStartDate(
                backUrl = routes.FirstIncomeSourceController.show(id).url,
                trade = "test trade"
              )

              val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

              status(result) mustBe OK
              contentType(result) mustBe Some(HTML)
            }
          }
        }
      }
    }
    "redirect to the streamline page" when {
      "there is no trade stored in the business" when {
        "the remove accounting method feature switch is enabled" in {
          enable(RemoveAccountingMethod)

          mockAuthSuccess()
          mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

          val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FullIncomeSourceController.show(id).url)
        }
        "the remove accounting method feature switch is disabled" when {
          "not in edit mode" in {
            mockAuthSuccess()
            mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

            val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id).url)
          }
          "in edit mode" in {
            mockAuthSuccess()
            mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

            val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = true, isGlobalEdit = false)(fakeRequest)

            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isEditMode = true).url)
          }
          "in global edit mode" in {
            mockAuthSuccess()
            mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

            val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = true)(fakeRequest)

            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isGlobalEdit = true).url)
          }
        }
      }
      "there is no business" when {
        "the remove accounting method feature switch is enabled" in {
          enable(RemoveAccountingMethod)

          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FullIncomeSourceController.show(id).url)
        }
        "not in edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id).url)
        }
        "in edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = true, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isEditMode = true).url)
        }
        "in global edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = true)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isGlobalEdit = true).url)
        }
      }
    }
    "throw an internal server exception" when {
      "there was a problem fetching the sole trader business" in {
        mockAuthSuccess()
        mockFetchBusiness(id)(Left(UnexpectedStatusFailure(INTERNAL_SERVER_ERROR)))

        intercept[InternalServerException](await(TestBusinessStartDateController.show(id = id, isEditMode = false, isGlobalEdit = true)(fakeRequest)))
          .message mustBe s"[BusinessStartDateController][show] - ${UnexpectedStatusFailure(INTERNAL_SERVER_ERROR).toString}"
      }
    }
  }

  "submit" must {
    "return a bad request" when {
      "an error is produced in the form" in {
        mockAuthSuccess()
        mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id = id, startDate = Some(DateModel.dateConvert(LocalDate.now)), trade = Some("test trade")))))
        mockGetClientDetails()
        mockBusinessStartDate(
          backUrl = routes.FirstIncomeSourceController.show(id).url,
          trade = "test trade"
        )

        val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

        status(result) mustBe BAD_REQUEST
        contentType(result) mustBe Some(HTML)
      }
    }

    "return a redirect to the streamline page" when {
      "an invalid date is submitted, and there is no trade stored in the business" when {
        "not in edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

          val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id).url)
        }
        "in edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

          val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = true, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isEditMode = true).url)
        }
        "in global edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(Some(SoleTraderBusiness(id))))

          val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = true)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isGlobalEdit = true).url)
        }
      }
      "an invalid date is submitted and there is no business stored" when {
        "not in edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id).url)
        }
        "in edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = true, isGlobalEdit = false)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isEditMode = true).url)
        }
        "in global edit mode" in {
          mockAuthSuccess()
          mockFetchBusiness(id)(Right(None))

          val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = true)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FirstIncomeSourceController.show(id, isGlobalEdit = true).url)
        }
      }
    }
    "save the start date and redirect to the address lookup initialise route" when {
      "not in edit mode or global edit mode" in {
        val date: DateModel = DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit)

        mockAuthSuccess()
        mockSaveBusinessStartDate(id, date)(Right(PostSubscriptionDetailsSuccessResponse))

        val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = false)(
          fakeRequest.withFormUrlEncodedBody(
            modelToFormData(date): _*
          )
        )

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.AddressLookupRoutingController.checkAddressLookupJourney(id).url)
      }
    }
    "save the start date and redirect to the check your answers page" when {
      "in edit mode" in {
        val date: DateModel = DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit)

        mockAuthSuccess()
        mockSaveBusinessStartDate(id, date)(Right(PostSubscriptionDetailsSuccessResponse))

        val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = true, isGlobalEdit = false)(
          fakeRequest.withFormUrlEncodedBody(
            modelToFormData(date): _*
          )
        )

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.SelfEmployedCYAController.show(id, isEditMode = true).url)
      }
      "in global edit mode" in {
        val date: DateModel = DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit)

        mockAuthSuccess()
        mockSaveBusinessStartDate(id, date)(Right(PostSubscriptionDetailsSuccessResponse))

        val result: Future[Result] = TestBusinessStartDateController.submit(id = id, isEditMode = false, isGlobalEdit = true)(
          fakeRequest.withFormUrlEncodedBody(
            modelToFormData(date): _*
          )
        )

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.SelfEmployedCYAController.show(id, isGlobalEdit = true).url)
      }
    }
  }

}
