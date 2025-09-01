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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.individual.routes
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.{AccountingPeriodUtil, ImplicitDateFormatter, ImplicitDateFormatterImpl, ViewSpec}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.SelfEmployedCYA

import java.time.format.DateTimeFormatter

class SelfEmployedCYAViewSpec extends ViewSpec with FeatureSwitching {

  override def beforeEach(): Unit = {
    super.beforeEach()
    disable(RemoveAccountingMethod)
  }

  val checkYourAnswers: SelfEmployedCYA = app.injector.instanceOf[SelfEmployedCYA]
  val implicitDateFormatter: ImplicitDateFormatter = app.injector.instanceOf[ImplicitDateFormatterImpl]
  val testId: String = "testId"
  val olderThanLimitDate: DateModel = DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit.minusDays(1))
  val limitDate: DateModel = DateModel.dateConvert(AccountingPeriodUtil.getStartDateLimit)

  val fullSelfEmploymentsCYAModel: SelfEmploymentsCYAModel = SelfEmploymentsCYAModel(
    id = testId,
    businessStartDate = Some(DateModel("1", "1", "2018")),
    businessName = Some(s"ABC Limited"),
    businessTradeName = Some(s"Plumbing"),
    businessAddress = Some(Address(Seq(s"line 1"), Some("TF3 4NT"))),
    accountingMethod = Some(Cash),
    totalSelfEmployments = 1,
    isFirstBusiness = true
  )

  val emptySelfEmploymentsCYAModel: SelfEmploymentsCYAModel = SelfEmploymentsCYAModel(
    id = testId,
    totalSelfEmployments = 1,
    isFirstBusiness = true
  )

  val multiBusinessCYAModel: SelfEmploymentsCYAModel = fullSelfEmploymentsCYAModel.copy(totalSelfEmployments = 2)

  def page(answers: SelfEmploymentsCYAModel = fullSelfEmploymentsCYAModel, isGlobalEdit: Boolean): HtmlFormat.Appendable = {
    checkYourAnswers(
      answers,
      testCall,
      Some(testBackUrl),
      isGlobalEdit = isGlobalEdit
    )(FakeRequest(), implicitly)
  }

  def document(answers: SelfEmploymentsCYAModel = fullSelfEmploymentsCYAModel, isGlobalEdit: Boolean = false): Document = {
    Jsoup.parse(page(answers, isGlobalEdit).body)
  }

  "Check Your Answers" must {

    "have the correct template details" in new TemplateViewTest(
      view = checkYourAnswers(
        answers = fullSelfEmploymentsCYAModel,
        postAction = testCall,
        Some(testBackUrl),
        isGlobalEdit = true
      )(FakeRequest(), implicitly),
      title = CheckYourAnswersMessages.title,
      hasSignOutLink = true,
      backLink = Some(testBackUrl)
    )

    "have the correct heading and caption" in {
      document().mainContent.mustHaveHeadingAndCaption(
        heading = CheckYourAnswersMessages.heading,
        caption = CheckYourAnswersMessages.captionVisual,
        isSection = true
      )
    }

    "have a summary of the self employment answers" when {
      "in edit mode" when {
        "the answers are complete" in {
          document().mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(Some("Plumbing")),
              nameRow(Some("ABC Limited")),
              startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel)),
              addressRow(Some("line 1 TF3 4NT")),
              accountingMethodRow(Some("Cash basis accounting"), multipleBusinesses = false)
            )
          )
        }
        "there exists multiple businesses" in {
          document(multiBusinessCYAModel).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(Some("Plumbing")),
              nameRow(Some("ABC Limited")),
              startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel)),
              addressRow(Some("line 1 TF3 4NT")),
              accountingMethodRow(Some("Cash basis accounting"), multipleBusinesses = true)
            )
          )
        }
        "the answers are not complete" in {
          document(emptySelfEmploymentsCYAModel).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(None),
              nameRow(None),
              startDateRow(None),
              addressRow(None),
              accountingMethodRow(None, multipleBusinesses = false)
            )
          )
        }
      }
      "in global mode" when {
        "the answers are complete" in {
          document(isGlobalEdit = true).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(Some("Plumbing"), globalEditMode = true),
              nameRow(Some("ABC Limited"), globalEditMode = true),
              startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel), globalEditMode = true),
              addressRow(Some("line 1 TF3 4NT"), globalEditMode = true),
              accountingMethodRow(Some("Cash basis accounting"), multipleBusinesses = false, globalEditMode = true)
            )
          )
        }
        "there exists multiple businesses" in {
          document(multiBusinessCYAModel, isGlobalEdit = true).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(Some("Plumbing"), globalEditMode = true),
              nameRow(Some("ABC Limited"), globalEditMode = true),
              startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel), globalEditMode = true),
              addressRow(Some("line 1 TF3 4NT"), globalEditMode = true),
              accountingMethodRow(Some("Cash basis accounting"), multipleBusinesses = true, globalEditMode = true)
            )
          )
        }
        "the answers are not complete" in {
          document(emptySelfEmploymentsCYAModel, isGlobalEdit = true).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(None, globalEditMode = true),
              nameRow(None, globalEditMode = true),
              startDateRow(None, globalEditMode = true),
              addressRow(None, globalEditMode = true),
              accountingMethodRow(None, multipleBusinesses = false, globalEditMode = true)
            )
          )
        }
      }
      "start date is before the limit" in {
        document(fullSelfEmploymentsCYAModel.copy(businessStartDate = Some(olderThanLimitDate))).mainContent.mustHaveSummaryList(".govuk-summary-list")(
          rows = Seq(
            tradeRow(Some("Plumbing")),
            nameRow(Some("ABC Limited")),
            startDateRow(value = Some(CheckYourAnswersMessages.startDateBeforeLimitLabel)),
            addressRow(Some("line 1 TF3 4NT")),
            accountingMethodRow(Some("Cash basis accounting"), multipleBusinesses = false)
          )
        )
      }
      "start date is after the limit" in {
        document(fullSelfEmploymentsCYAModel.copy(businessStartDate = Some(limitDate))).mainContent.mustHaveSummaryList(".govuk-summary-list")(
          rows = Seq(
            tradeRow(Some("Plumbing")),
            nameRow(Some("ABC Limited")),
            startDateRow(value = Some(limitDate.toLocalDate.format(DateTimeFormatter.ofPattern("d MMMM yyy")))),
            addressRow(Some("line 1 TF3 4NT")),
            accountingMethodRow(Some("Cash basis accounting"), multipleBusinesses = false)
          )
        )
      }
    }

    "have a summary of the self employment answers without accounting method" when {
      "remove accounting method feature switch is enabled" when {
        "in edit mode" when {
          "the answers are complete" in {
            enable(RemoveAccountingMethod)
            document().mainContent.mustHaveSummaryList(".govuk-summary-list")(
              rows = Seq(
                tradeRow(Some("Plumbing")),
                nameRow(Some("ABC Limited")),
                startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel)),
                addressRow(Some("line 1 TF3 4NT"))
              )
            )
          }
          "there exists multiple businesses" in {
            enable(RemoveAccountingMethod)
            document(multiBusinessCYAModel).mainContent.mustHaveSummaryList(".govuk-summary-list")(
              rows = Seq(
                tradeRow(Some("Plumbing")),
                nameRow(Some("ABC Limited")),
                startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel)),
                addressRow(Some("line 1 TF3 4NT"))
              )
            )
          }
          "the answers are not complete" in {
            enable(RemoveAccountingMethod)
            document(emptySelfEmploymentsCYAModel).mainContent.mustHaveSummaryList(".govuk-summary-list")(
              rows = Seq(
                tradeRow(None),
                nameRow(None),
                startDateRow(None),
                addressRow(None)
              )
            )
          }
        }
        "in global mode" when {
          "the answers are complete" in {
            enable(RemoveAccountingMethod)
            document(isGlobalEdit = true).mainContent.mustHaveSummaryList(".govuk-summary-list")(
              rows = Seq(
                tradeRow(Some("Plumbing"), globalEditMode = true),
                nameRow(Some("ABC Limited"), globalEditMode = true),
                startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel), globalEditMode = true),
                addressRow(Some("line 1 TF3 4NT"), globalEditMode = true)
              )
            )
          }
          "there exists multiple businesses" in {
            enable(RemoveAccountingMethod)
            document(multiBusinessCYAModel, isGlobalEdit = true).mainContent.mustHaveSummaryList(".govuk-summary-list")(
              rows = Seq(
                tradeRow(Some("Plumbing"), globalEditMode = true),
                nameRow(Some("ABC Limited"), globalEditMode = true),
                startDateRow(Some(CheckYourAnswersMessages.startDateBeforeLimitLabel), globalEditMode = true),
                addressRow(Some("line 1 TF3 4NT"), globalEditMode = true)
              )
            )
          }
          "the answers are not complete" in {
            enable(RemoveAccountingMethod)
            document(emptySelfEmploymentsCYAModel, isGlobalEdit = true).mainContent.mustHaveSummaryList(".govuk-summary-list")(
              rows = Seq(
                tradeRow(None, globalEditMode = true),
                nameRow(None, globalEditMode = true),
                startDateRow(None, globalEditMode = true),
                addressRow(None, globalEditMode = true)
              )
            )
          }
        }
        "start date is before the limit" in {
          enable(RemoveAccountingMethod)
          document(fullSelfEmploymentsCYAModel.copy(businessStartDate = Some(olderThanLimitDate))).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(Some("Plumbing")),
              nameRow(Some("ABC Limited")),
              startDateRow(value = Some(CheckYourAnswersMessages.startDateBeforeLimitLabel)),
              addressRow(Some("line 1 TF3 4NT"))
            )
          )
        }
        "start date is after the limit" in {
          enable(RemoveAccountingMethod)
          document(fullSelfEmploymentsCYAModel.copy(businessStartDate = Some(limitDate))).mainContent.mustHaveSummaryList(".govuk-summary-list")(
            rows = Seq(
              tradeRow(Some("Plumbing")),
              nameRow(Some("ABC Limited")),
              startDateRow(value = Some(limitDate.toLocalDate.format(DateTimeFormatter.ofPattern("d MMMM yyy")))),
              addressRow(Some("line 1 TF3 4NT"))
            )
          )
        }
      }
    }

    "have a form" which {
      def form: Element = document().mainContent.getForm

      "has the correct attributes" in {
        form.attr("method") mustBe testCall.method
        form.attr("action") mustBe testCall.url
      }
      "has a confirm and continue button" in {
        form.selectNth(".govuk-button", 1).text mustBe CheckYourAnswersMessages.confirmAndContinue
      }
      "has a save and come back later button" in {
        val saveAndComeBackLater = form.selectNth(".govuk-button", 2)
        saveAndComeBackLater.text mustBe CheckYourAnswersMessages.saveAndBack
        saveAndComeBackLater.attr("href") mustBe s"${appConfig.subscriptionFrontendProgressSavedUrl}?location=sole-trader-check-your-answers"
      }
    }

  }


  def simpleSummaryRow(key: String): (Option[String], Boolean) => SummaryListRowValues = {
    case (value, globalEditMode) =>
      SummaryListRowValues(
        key = key,
        value = value,
        actions = Seq(
          SummaryListActionValues(
            href = routes.FullIncomeSourceController.show(testId, isEditMode = true, isGlobalEdit = globalEditMode).url,
            text = (if (value.isDefined) CheckYourAnswersMessages.change else CheckYourAnswersMessages.add) + " " + key,
            visuallyHidden = key
          )
        )
      )
  }

  private def tradeRow(value: Option[String], globalEditMode: Boolean = false) = {
    simpleSummaryRow(CheckYourAnswersMessages.businessTrade)(value, globalEditMode)
  }

  private def nameRow(value: Option[String], globalEditMode: Boolean = false) = {
    simpleSummaryRow(CheckYourAnswersMessages.businessName)(value, globalEditMode)
  }

  private def startDateRow(value: Option[String], globalEditMode: Boolean = false) = {
    simpleSummaryRow(CheckYourAnswersMessages.tradingStartDate)(value, globalEditMode)
  }

  private def addressRow(value: Option[String], globalEditMode: Boolean = false) = SummaryListRowValues(
    key = CheckYourAnswersMessages.businessAddress,
    value = value,
    actions = Seq(
      SummaryListActionValues(
        href = routes.AddressLookupRoutingController.initialiseAddressLookupJourney(testId, isEditMode = true, isGlobalEdit = globalEditMode).url,
        text = (if (value.isDefined) CheckYourAnswersMessages.change else CheckYourAnswersMessages.add) + " " + CheckYourAnswersMessages.businessAddress,
        visuallyHidden = CheckYourAnswersMessages.businessAddress
      )
    )
  )

  private def accountingMethodRow(value: Option[String], multipleBusinesses: Boolean, globalEditMode: Boolean = false) = SummaryListRowValues(
    key = CheckYourAnswersMessages.accountingMethod,
    value = value,
    actions = Seq(
      SummaryListActionValues(
        href = if (multipleBusinesses) {
          routes.ChangeAccountingMethodController.show(testId, isGlobalEdit = globalEditMode).url
        } else {
          routes.BusinessAccountingMethodController.show(testId, isEditMode = true, isGlobalEdit = globalEditMode).url
        },
        text = (if (value.isDefined) CheckYourAnswersMessages.change else CheckYourAnswersMessages.add) + s" ${CheckYourAnswersMessages.accountingMethod}",
        visuallyHidden = CheckYourAnswersMessages.accountingMethod
      )
    )
  )

  object CheckYourAnswersMessages {
    val captionVisual = "Sole trader"
    val heading = "Check your answers"
    val title = "Check your answers - sole trader business"
    val confirmAndContinue = "Confirm and continue"
    val saveAndBack = "Save and come back later"
    val change = "Change"
    val add = "Add"
    val tradingStartDate = "Start date"
    val businessName = "Business name"
    val businessAddress = "Address"
    val businessTrade = "Trade"
    val accountingMethod = "Accounting method for sole trader income"
    val startDateBeforeLimitLabel = s"Before 6 April ${AccountingPeriodUtil.getStartDateLimit.getYear}"
  }
}
