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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.agent

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.data.FormError
import play.twirl.api.Html
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.BusinessStartDateForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.ClientDetails
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.{AccountingPeriodUtil, ViewSpec}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.agent.BusinessStartDate

class BusinessStartDateViewSpec extends ViewSpec with FeatureSwitching {

  object BusinessStartDateMessages {
    val title: String = "Sole trader"
    val caption: String = "FirstName LastName – ZZ 11 11 11 Z"
    val heading: String = "Business start date"
    val legend: String = "When did your client’s business start trading?"
    val para: String = "The date your client’s business started trading can be today, in the past or up to 7 days in the future."
    val question: String = "When did your client’s business start trading?"
    val hint: String = s"For example, 27 9 ${AccountingPeriodUtil.getCurrentTaxYearStartDate.getYear}"
    val saveAndContinue = "Save and continue"
    val saveAndComeBackLater = "Save and come back later"
    val emptyError = "Enter the date your client’s business started trading"
    val dateTooLateError = s"The date cannot be more than 7 days in the future"
    val dateTooEarlyError = "The date must be on or after 11 April 2021"
  }

  val businessStartDateView: BusinessStartDate = app.injector.instanceOf[BusinessStartDate]

  def page(error: Option[FormError] = None): Html = {
    val form = BusinessStartDateForm.businessStartDateForm(BusinessStartDateForm.minStartDate, BusinessStartDateForm.maxStartDate, d => d.toString)
    businessStartDateView(
      businessStartDateForm = error match {
        case Some(value) => form.withError(value)
        case None => form
      },
      postAction = testCall,
      clientDetails = ClientDetails("FirstName LastName", "ZZ111111Z"),
      businessTrade = "test trade"
    )(fakeTestRequest, implicitly)
  }

  def document(error: Option[FormError] = None): Document =
    Jsoup.parse(page(error).body)

  private val emptyFormError = FormError(BusinessStartDateForm.startDate, "agent.error.business-start-date.empty")
  private val dateTooLateFormError = FormError("startDate", "agent.error.business-start-date.day-month-year.max-date", List("11 April 2021"))
  private val dateTooEarlyFormError = FormError("startDate", "agent.error.business-start-date.day-month-year.min-date", List("11 April 2021"))

  "BusinessStartDate" must {
    "have the correct template details" when {
      "there is no error on the page" in new TemplateViewTest(
        view = page(),
        title = BusinessStartDateMessages.title,
        isAgent = true,
        hasSignOutLink = true
      )
      "there is an error on the page" in new TemplateViewTest(
        view = page(error = Some(emptyFormError)),
        title = BusinessStartDateMessages.title,
        isAgent = true,
        hasSignOutLink = true,
        errors = Some(Seq(emptyFormError.key -> BusinessStartDateMessages.emptyError))
      )
    }

    "have the correct heading and caption" in {
      document().mainContent.mustHaveHeadingAndCaption(
        heading = BusinessStartDateMessages.heading,
        caption = BusinessStartDateMessages.caption,
        isSection = false
      )
    }

    "have a paragraph" in {
      document().mainContent.selectHead("p").text mustBe BusinessStartDateMessages.para
    }

    "have a form" which {
      "has the correct action and method assigned" in {
        val form: Element = document().getForm
        form.attr("method") mustBe testCall.method
        form.attr("action") mustBe testCall.url
      }

      "has a correct date input field with the legend as the expected message" when {
        "there is no error on the page" in {
          document().getForm.mustHaveDateInput(
            id = "startDate",
            legend = BusinessStartDateMessages.legend,
            exampleDate = BusinessStartDateMessages.hint,
            isHeading = false,
            isLegendHidden = false,
            dateInputsValues = Seq(
              DateInputFieldValues("Day", None),
              DateInputFieldValues("Month", None),
              DateInputFieldValues("Year", None)
            )
          )
        }

        "has a form empty error on page" in {
          val doc = document(error = Some(emptyFormError))

          doc.mustHaveDateInput(
            id = "startDate",
            legend = BusinessStartDateMessages.legend,
            exampleDate = BusinessStartDateMessages.hint,
            errorMessage = Some(BusinessStartDateMessages.emptyError),
            isHeading = false,
            isLegendHidden = false,
            dateInputsValues = Seq(
              DateInputFieldValues("Day", None),
              DateInputFieldValues("Month", None),
              DateInputFieldValues("Year", None)
            )
          )
        }

        "has a max date error on page" in {
          val doc = document(error = Some(dateTooLateFormError))

          doc.mustHaveDateInput(
            id = "startDate",
            legend = BusinessStartDateMessages.legend,
            exampleDate = BusinessStartDateMessages.hint,
            errorMessage = Some(BusinessStartDateMessages.dateTooLateError),
            isHeading = false,
            isLegendHidden = false,
            dateInputsValues = Seq(
              DateInputFieldValues("Day", None),
              DateInputFieldValues("Month", None),
              DateInputFieldValues("Year", None)
            )
          )
        }

        "has a min date error on page" in {
          val doc = document(error = Some(dateTooEarlyFormError))

          doc.mustHaveDateInput(
            id = "startDate",
            legend = BusinessStartDateMessages.legend,
            exampleDate = BusinessStartDateMessages.hint,
            errorMessage = Some(BusinessStartDateMessages.dateTooEarlyError),
            isHeading = false,
            isLegendHidden = false,
            dateInputsValues = Seq(
              DateInputFieldValues("Day", None),
              DateInputFieldValues("Month", None),
              DateInputFieldValues("Year", None)
            )
          )
        }
      }

      "has buttons" which {
        "include the save and continue button" in {
          document().getForm.getGovukButton.text mustBe BusinessStartDateMessages.saveAndContinue
        }
        "include the save and come back later link" in {
          val saveAndComeBackLink = document().selectHead("a[role=button]")
          saveAndComeBackLink.text mustBe BusinessStartDateMessages.saveAndComeBackLater
          saveAndComeBackLink.attr("href") mustBe
            appConfig.subscriptionFrontendClientProgressSavedUrl + "?location=sole-trader-trading-start-date"
        }
      }
    }
  }
}
