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

import connectors.stubs.IncomeTaxSubscriptionConnectorStub._
import connectors.stubs.SessionDataConnectorStub.stubGetSessionData
import helpers.ComponentSpecBase
import helpers.IntegrationTestConstants._
import helpers.servicemocks.AuthStub._
import play.api.http.Status._
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.SelfEmploymentDataKeys.{incomeSourcesComplete, soleTraderBusinessesKey}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.individual.{FullIncomeSourceController, routes}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.individual.StreamlineIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{Cash, DateModel, SoleTraderBusinesses}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.{AccountingPeriodUtil, ITSASessionKeys}

class FullIncomeSourceControllerISpec extends ComponentSpecBase with FeatureSwitching {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val fullIncomeSourceController: FullIncomeSourceController = app.injector.instanceOf[FullIncomeSourceController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    disable(RemoveAccountingMethod)
  }

  val clearedSoleTraderBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(
    businesses = Seq(soleTraderBusiness.copy(
      trade = None,
      name = None,
      startDate = None,
      startDateBeforeLimit = None
    )),
    accountingMethod = Some(Cash))


  s"GET ${routes.FullIncomeSourceController.show(id)}" when {
    "the connector returns an error from the backend" should {
      "display the technical difficulties page" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(INTERNAL_SERVER_ERROR)
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = false, isGlobalEdit = false)

        Then("should return an INTERNAL_SERVER_ERROR")
        res must have(
          httpStatus(INTERNAL_SERVER_ERROR)
        )
      }
    }
    "the connector returns a business with no fields populated" should {
      "return the page with no prepopulated fields" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = false, isGlobalEdit = false)

        Then("should return OK with the full income source page")
        res must have(
          httpStatus(OK),
          pageTitle(messages("individual.full-income-source.heading") + titleSuffix),
          textField(StreamlineIncomeSourceForm.businessTradeName, ""),
          textField(StreamlineIncomeSourceForm.businessName, ""),
          radioButtonSet(StreamlineIncomeSourceForm.startDateBeforeLimit, None),
        )
      }
    }
    "the connector returns a complete business with a stored start date after limit" should {
      "return the page with correct fields" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(
          responseStatus = OK,
          responseBody = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(
            startDateBeforeLimit = None)))))
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = false, isGlobalEdit = false)

        Then("should return OK with the full income source page")
        res must have(
          httpStatus(OK),
          pageTitle(messages("individual.full-income-source.heading") + titleSuffix),
          textField(StreamlineIncomeSourceForm.businessTradeName, "test trade"),
          textField(StreamlineIncomeSourceForm.businessName, "test name"),
          radioButtonSet(StreamlineIncomeSourceForm.startDateBeforeLimit, Some("No")),
        )
      }
    }
    "the connector returns a complete business with a stored start date before limit" should {
      "return the page with correct fields" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(
          responseStatus = OK,
          responseBody = Json.toJson(
            soleTraderBusinesses.copy(
              businesses = Seq(
                soleTraderBusiness.copy(
                  startDateBeforeLimit = None,
                  startDate = Some(DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit.minusDays(1)))
                )
              )
            )
          ))
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = false, isGlobalEdit = false)

        Then("should return OK with the full income source page")
        res must have(
          httpStatus(OK),
          pageTitle(messages("individual.full-income-source.heading") + titleSuffix),
          textField(StreamlineIncomeSourceForm.businessTradeName, "test trade"),
          textField(StreamlineIncomeSourceForm.businessName, "test name"),
          radioButtonSet(StreamlineIncomeSourceForm.startDateBeforeLimit, Some("Yes")),
        )
      }
    }
    "the connector returns a complete business with start date before limit selected" should {
      "return the page with correct fields" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(
          responseStatus = OK,
          responseBody = Json.toJson(
            soleTraderBusinesses.copy(
              businesses = Seq(
                soleTraderBusiness.copy(
                  startDateBeforeLimit = Some(true),
                  startDate = None)
              )
            )
          )
        )
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = false, isGlobalEdit = false)

        Then("should return OK with the full income source page")
        res must have(
          httpStatus(OK),
          pageTitle(messages("individual.full-income-source.heading") + titleSuffix),
          textField(StreamlineIncomeSourceForm.businessTradeName, "test trade"),
          textField(StreamlineIncomeSourceForm.businessName, "test name"),
          radioButtonSet(StreamlineIncomeSourceForm.startDateBeforeLimit, Some("Yes")),
        )
      }
    }
    "the connector returns a previously filled in business which had selected their start date is after the limit but the stored date is before" should {
      "return the page with correct fields for current business" in {

        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(
          responseStatus = OK,
          responseBody = Json.toJson(
            soleTraderBusinesses.copy(
              businesses = Seq(
                soleTraderBusiness.copy(
                  startDateBeforeLimit = Some(false),
                  startDate = Some(DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit.minusDays(1)))
                )
              )
            )
          )
        )
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = true, isGlobalEdit = false)

        Then("should return an OK with the full income source page")
        res must have(
          httpStatus(OK),
          pageTitle(messages("individual.full-income-source.heading") + titleSuffix),
          textField(StreamlineIncomeSourceForm.businessTradeName, "test trade"),
          textField(StreamlineIncomeSourceForm.businessName, "test name"),
          radioButtonSet(StreamlineIncomeSourceForm.startDateBeforeLimit, Some("Yes")),
        )
      }
    }
    "the connector returns a previously filled in business which had selected their start date is after the limit and it still is" should {
      "return the page with correct fields for current business" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()
        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(
          responseStatus = OK,
          responseBody = Json.toJson(
            soleTraderBusinesses.copy(
              businesses = Seq(
                soleTraderBusiness.copy(
                  startDateBeforeLimit = Some(false),
                  startDate = Some(DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit))
                )
              )
            )
          )
        )
        stubGetSessionData(ITSASessionKeys.NINO)(OK, JsString(testNino))

        When(s"GET ${routes.FullIncomeSourceController.show(id)} is called")
        val res = getFullIncomeSource(id, isEditMode = true, isGlobalEdit = false)

        Then("should return an OK with the first sole trader business page")
        res must have(
          httpStatus(OK),
          pageTitle(messages("individual.full-income-source.heading") + titleSuffix),
          textField(StreamlineIncomeSourceForm.businessTradeName, "test trade"),
          textField(StreamlineIncomeSourceForm.businessName, "test name"),
          radioButtonSet(StreamlineIncomeSourceForm.startDateBeforeLimit, Some("No")),
        )
      }
    }
  }

  s"POST ${routes.FullIncomeSourceController.submit(id)}" when {
    "the connector returns an error when saving the streamline business" should {
      "display the technical difficulties page" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()

        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
        stubSaveSubscriptionData(
          reference = reference,
          id = soleTraderBusinessesKey,
          body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(
            startDate = None,
            startDateBeforeLimit = Some(true)))))
        )(INTERNAL_SERVER_ERROR)
        stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

        When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
        val res = submitFullIncomeSource(
          trade = Some("test trade"),
          name = Some("test name"),
          startDateBeforeLimit = Some(true),
          id = id,
          isEditMode = false,
          isGlobalEdit = false
        )

        Then("should return an INTERNAL_SERVER_ERROR")
        res must have(
          httpStatus(INTERNAL_SERVER_ERROR)
        )
      }
    }
    "the connector returns an error when fetching the streamline business" should {
      "display the technical difficulties page" in {
        Given("I setup the Wiremock stubs")
        stubAuthSuccess()

        stubGetSubscriptionData(reference, soleTraderBusinessesKey)(INTERNAL_SERVER_ERROR, Json.toJson(clearedSoleTraderBusinesses))

        When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
        val res = submitFullIncomeSource(
          trade = Some("test trade"),
          name = Some("test name"),
          startDateBeforeLimit = Some(true),
          id = id,
          isEditMode = false,
          isGlobalEdit = false
        )

        Then("should return an INTERNAL_SERVER_ERROR")
        res must have(
          httpStatus(INTERNAL_SERVER_ERROR)
        )
      }
    }
    "not in edit mode" when {
      "form data is valid" should {
        "return SEE_OTHER and redirect to start date page when user selects 'No' to start date before limit" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(false)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
          val res = submitFullIncomeSource(
            trade = Some("test trade"),
            name = Some("test name"),
            startDateBeforeLimit = Some(false),
            id = id, isEditMode = false, isGlobalEdit = false
          )

          Then("return SEE_OTHER and redirect to start date page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(routes.BusinessStartDateController.show(id).url)
          )
        }

        "return SEE_OTHER and redirect to address lookup journey when user selects 'Yes' to start date before limit" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(true)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
          val res = submitFullIncomeSource(
            trade = Some("test trade"),
            name = Some("test name"),
            startDateBeforeLimit = Some(true),
            id = id, isEditMode = false, isGlobalEdit = false
          )

          Then("return SEE_OTHER and redirect to address lookup journey")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(routes.AddressLookupRoutingController.checkAddressLookupJourney(id).url)
          )
        }
      }
      "form data is invalid" should {
        "return BAD_REQUEST" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(false)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
          val res = submitFullIncomeSource(
            trade = None,
            name = Some("test name"),
            startDateBeforeLimit = Some(false),
            id = id, isEditMode = false, isGlobalEdit = false
          )

          Then("return BAD_REQUEST")
          res must have(httpStatus(BAD_REQUEST))
        }
      }
    }
    "in edit mode" when {
      "form data is valid" should {
        "return SEE_OTHER and redirect to start date page when user selects 'No' to start date before limit" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(false)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id, isEditMode = true)} is called")
          val res = submitFullIncomeSource(
            trade = Some("test trade"),
            name = Some("test name"),
            startDateBeforeLimit = Some(false),
            id = id, isEditMode = true, isGlobalEdit = false
          )

          Then("return SEE_OTHER and redirect to start date page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(routes.BusinessStartDateController.show(id, isEditMode = true).url)
          )
        }

        "return SEE_OTHER and redirect to sole trader CYA when user selects 'Yes' to start date before limit" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(true)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id, isEditMode = true)} is called")
          val res = submitFullIncomeSource(
            trade = Some("test trade"),
            name = Some("test name"),
            startDateBeforeLimit = Some(true),
            id = id, isEditMode = true, isGlobalEdit = false
          )

          Then("return SEE_OTHER and redirect sole trader CYA")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(routes.SelfEmployedCYAController.show(id, isEditMode = true).url)
          )
        }
      }
      "form data is invalid" should {
        "return BAD_REQUEST" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(false)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id, isEditMode = true)} is called")
          val res = submitFullIncomeSource(
            trade = None,
            name = Some("test name"),
            startDateBeforeLimit = Some(false),
            id = id, isEditMode = true, isGlobalEdit = false
          )

          Then("return BAD_REQUEST")
          res must have(httpStatus(BAD_REQUEST))
        }
      }
    }
    "in global edit mode" when {
      "form data is valid" should {
        "return SEE_OTHER and redirect to start date page when user selects 'No' to start date before limit" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(false)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id, isGlobalEdit = true)} is called")
          val res = submitFullIncomeSource(
            trade = Some("test trade"),
            name = Some("test name"),
            startDateBeforeLimit = Some(false),
            id = id, isEditMode = false, isGlobalEdit = true
          )

          Then("return SEE_OTHER and redirect to start date page")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(routes.BusinessStartDateController.show(id, isGlobalEdit = true).url)
          )
        }

        "return SEE_OTHER and redirect to sole trader CYA when user selects 'Yes' to start date before limit" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(true)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
          val res = submitFullIncomeSource(
            trade = Some("test trade"),
            name = Some("test name"),
            startDateBeforeLimit = Some(true),
            id = id, isEditMode = false, isGlobalEdit = true
          )

          Then("return SEE_OTHER and redirect to sole trader CYA")
          res must have(
            httpStatus(SEE_OTHER),
            redirectURI(routes.SelfEmployedCYAController.show(id, isGlobalEdit = true).url)
          )
        }
      }
      "form data is invalid" should {
        "return BAD_REQUEST" in {
          Given("I setup the Wiremock stubs")
          stubAuthSuccess()
          stubGetSubscriptionData(reference, soleTraderBusinessesKey)(OK, Json.toJson(clearedSoleTraderBusinesses))
          stubSaveSubscriptionData(
            reference = reference,
            id = soleTraderBusinessesKey,
            body = Json.toJson(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None, startDateBeforeLimit = Some(false)))))
          )(OK)
          stubDeleteSubscriptionData(reference, incomeSourcesComplete)(OK)

          When(s"POST ${routes.FullIncomeSourceController.submit(id)} is called")
          val res = submitFullIncomeSource(
            trade = None,
            name = Some("test name"),
            startDateBeforeLimit = Some(false),
            id = id, isEditMode = false, isGlobalEdit = true
          )

          Then("return BAD_REQUEST")
          res must have(httpStatus(BAD_REQUEST))
        }
      }
    }
  }

  "backUrl" when {
    def backUrl(isEditMode: Boolean, isGlobalEdit: Boolean, isFirstBusiness: Boolean): String =
      fullIncomeSourceController.backUrl(id, isEditMode, isGlobalEdit, isFirstBusiness)

    "not in edit mode" should {
      "redirect to accounting method when it is the first business" in {
        backUrl(isEditMode = false, isGlobalEdit = false, isFirstBusiness = true) mustBe routes.BusinessAccountingMethodController.show(id).url
      }
      "redirect to your income sources page when it is not the first business" in {
        backUrl(isEditMode = false, isGlobalEdit = false, isFirstBusiness = false) mustBe appConfig.yourIncomeSourcesUrl
      }
    }

    "in edit mode" should {
      "redirect to sole trader CYA when it is the first business" in {
        backUrl(isEditMode = true, isGlobalEdit = false, isFirstBusiness = true) mustBe routes.SelfEmployedCYAController.show(id, isEditMode = true).url
      }
      "redirect to sole trader CYA when it is not the first business" in {
        backUrl(isEditMode = true, isGlobalEdit = false, isFirstBusiness = false) mustBe routes.SelfEmployedCYAController.show(id, isEditMode = true).url
      }
    }

    "in global edit mode" should {
      "redirect to sole trader CYA when it is the first business" in {
        backUrl(isEditMode = true, isGlobalEdit = true, isFirstBusiness = true) mustBe routes.SelfEmployedCYAController.show(id, isEditMode = true, isGlobalEdit = true).url
      }
      "redirect to sole trader CYA when it is not the first business" in {
        backUrl(isEditMode = true, isGlobalEdit = true, isFirstBusiness = false) mustBe routes.SelfEmployedCYAController.show(id, isEditMode = true, isGlobalEdit = true).url
      }
    }

    "when the feature switch is enabled" should {
      "redirect to your income sources page when its the first business without accounting method" in {
        enable(RemoveAccountingMethod)
        backUrl(isEditMode = false, isGlobalEdit = false, isFirstBusiness = true) mustBe appConfig.yourIncomeSourcesUrl
      }
    }
  }
}

