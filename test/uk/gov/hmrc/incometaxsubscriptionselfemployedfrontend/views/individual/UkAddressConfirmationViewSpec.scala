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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.individual

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.data.FormError
import play.twirl.api.Html
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.individual.UkAddressConfirmationForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.individual.UkAddressConfirmationForm.*
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.submapping.YesNoMapping
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{Address, Country}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ViewSpec
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.individual.UkAddressConfirmation

class UkAddressConfirmationViewSpec extends ViewSpec {

  val ukAddressConfirmation: UkAddressConfirmation = app.injector.instanceOf[UkAddressConfirmation]
  val testFormError: FormError = FormError(key = fieldName, message = "error.individual.uk-address-confirmation.empty")
  val testName = "Test business"

  def page(hasFormError: Boolean = false): Html = {
    ukAddressConfirmation(
      if (hasFormError) {
        ukAddressConfirmationForm.withError(testFormError)
      } else {
        ukAddressConfirmationForm
      },
      testCall,
      testName
    )(fakeTestRequest, implicitly)
  }

  def document(hasFormError: Boolean = false): Document = {
    Jsoup.parse(page(hasFormError).body)
  }

  object UkAddressConfirmationMessages {
    val title = "Is the business address in the UK?"
    val heading = s"Is the address for your business, $testName in the UK?"
    val hidden = "Select if your sole trader business address is in the UK"
    val caption = "Sole trader"

    object Form {
      val yes: String = "Yes"
      val no: String = "No"
      val emptyError: String = "Select if business address is in the UK"
      val saveAndContinue: String = "Save and continue"
      val saveAndComeBackLater: String = "Save and come back later"
    }
  }

  "Business Address Confirmation" must {
    "use the correct page template" when {
      "there is no error on the page" in new TemplateViewTest(
        page(),
        title = UkAddressConfirmationMessages.title,
        isAgent = false,
        hasSignOutLink = true
      )
      "there is an error on the page" in new TemplateViewTest(
        page(hasFormError = true),
        title = UkAddressConfirmationMessages.title,
        isAgent = false,
        hasSignOutLink = true,
        errors = Some(Seq(testFormError.key -> UkAddressConfirmationMessages.Form.emptyError))
      )
    }

    "have the correct heading and caption" in {
      document().mainContent.mustHaveHeadingAndCaption(
        heading = UkAddressConfirmationMessages.heading,
        caption = UkAddressConfirmationMessages.caption,
        isSection = true
      )
    }

    "have a form" which {
      def form: Element = document().mainContent.getForm

      "has the correct attributes" in {
        form.attr("method") mustBe testCall.method
        form.attr("action") mustBe testCall.url
      }

      "has the correct inline radio inputs" in {
        document().mustHaveRadioInput(
          selector = "fieldset"
        )(
          name = fieldName,
          legend = UkAddressConfirmationMessages.hidden,
          isHeading = false,
          isLegendHidden = true,
          hint = None,
          errorMessage = None,
          radioContents = Seq(
            RadioItem(
              content = Text(UkAddressConfirmationMessages.Form.yes),
              value = Some(YesNoMapping.option_yes)
            ),
            RadioItem(
              content = Text(UkAddressConfirmationMessages.Form.no),
              value = Some(YesNoMapping.option_no)
            )
          )
        )
      }

      "has a save and continue button" in {
        val button = form.selectHead(".govuk-button")
        button.text mustBe UkAddressConfirmationMessages.Form.saveAndContinue
      }

      "has a save and come back later button" in {
        val button = form.selectHead(".govuk-button--secondary")
        button.attr("href") mustBe s"${appConfig.subscriptionFrontendProgressSavedUrl}?location=uk-address-confirmation"
        button.attr("role") mustBe "button"
        button.text mustBe UkAddressConfirmationMessages.Form.saveAndComeBackLater
      }
    }
  }

}
