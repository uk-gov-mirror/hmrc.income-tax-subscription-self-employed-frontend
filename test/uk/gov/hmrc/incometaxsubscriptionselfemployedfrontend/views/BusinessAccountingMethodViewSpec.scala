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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.BusinessAccountingMethodForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.AccountingMethodModel
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ViewSpec
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.business_accounting_method

class BusinessAccountingMethodViewSpec extends ViewSpec {
  val backUrl: String = testBackUrl
  val action: Call = testCall
  val emptyError = "Select if you use cash accounting or standard accounting"

  object BusinessAccountingMethodMessages {
    val title = "How do you record your income and expenses for your self-employed business?"
    val heading: String = title
    val accordion = "Show me an example"
    val accordionLine_1 = "You created an invoice for someone in March 2017, but did not receive the money until May 2017. If you tell HMRC you received this income in:"
    val accordionBullet_1 = "May 2017, you use ‘cash accounting’"
    val accordionBullet_2 = "March 2017, you use ‘standard accounting’"
    val cash = "Cash accounting"
    val cashDescription = "You record on the date you receive money or pay a bill. Most sole traders and small businesses use this method."
    val accruals = "Standard accounting"
    val accrualsDescription = "You record on the date you send or receive an invoice, even if you do not receive or pay any money. This is also called ‘accruals’ or ‘traditional accounting’."
    val continue = "Continue"
    val backLink = "Back"
  }

  class Setup(businessAccountingMethodForm: Form[AccountingMethodModel] = BusinessAccountingMethodForm.businessAccountingMethodForm) {
    val page: HtmlFormat.Appendable = business_accounting_method(
      businessAccountingMethodForm,
      testCall,
      testBackUrl
    )(FakeRequest(), implicitly, appConfig)

    val document: Document = Jsoup.parse(page.body)
  }

  "Business Accounting Method Page" must {

    "have a title" in new Setup {
      document.title mustBe BusinessAccountingMethodMessages.title
    }

    "have a backlink" in new Setup {
      document.getBackLink.text mustBe BusinessAccountingMethodMessages.backLink
      document.getBackLink.attr("href") mustBe testBackUrl
    }

    "have a heading" in new Setup {
      document.getH1Element.text mustBe BusinessAccountingMethodMessages.heading

    }


    "have an accordion summary" in new Setup {
      document.select("details summary span.summary").text() mustBe BusinessAccountingMethodMessages.accordion

    }

    "have an accordion heading" in new Setup {
      document.getParagraphNth(3) mustBe BusinessAccountingMethodMessages.accordionLine_1

    }

    "have an accordion bullets list 1" in new Setup {
      document.getBulletPointNth() mustBe BusinessAccountingMethodMessages.accordionBullet_1
    }

    "have an accordion bullets list 2" in new Setup {
      document.getBulletPointNth(1) mustBe BusinessAccountingMethodMessages.accordionBullet_2
    }

    //radio button test
    "have a radio button for cash accounting" in new Setup {
      document.getRadioButtonByIndex(0).select("#businessAccountingMethod-Cash").size() mustBe 1
    }

    "have a cash accounting heading for the radio button" in new Setup {
      document.getRadioButtonByIndex(0).select("label span").text() mustBe BusinessAccountingMethodMessages.cash
    }

    "have the correct description for the cash accounting radio button" in new Setup {
      val startIndex: Int = 16
      document.getRadioButtonByIndex(0).select("label").text().substring(startIndex) mustBe BusinessAccountingMethodMessages.cashDescription
    }

    "have a radio button for standard accounting" in new Setup {
      document.getRadioButtonByIndex(1).select("#businessAccountingMethod-Standard").size() mustBe 1
    }

    "have a standard accounting heading for the radio button" in new Setup {
      document.getRadioButtonByIndex(1).select("label span").text() mustBe BusinessAccountingMethodMessages.accruals
    }

    "have the correct description for the standard accounting radio button" in new Setup {
      val startIndex: Int = 20
      document.getRadioButtonByIndex(1).select("label").text().substring(startIndex) mustBe BusinessAccountingMethodMessages.accrualsDescription
    }

    "have a Form" in new Setup {
      document.getForm.attr("method") mustBe testCall.method
      document.getForm.attr("action") mustBe testCall.url
    }

    "have a continue button" in new Setup {
      document.getSubmitButton.text mustBe BusinessAccountingMethodMessages.continue
    }

  }

  "must display empty form error summary when submit with an empty form" in new Setup(BusinessAccountingMethodForm.businessAccountingMethodForm.withError("", emptyError)) {
    document.mustHaveErrorSummary(List[String](emptyError))
  }

  "must display empty form error message when submit with an empty form" in new Setup(BusinessAccountingMethodForm.businessAccountingMethodForm.withError(BusinessAccountingMethodForm.businessAccountingMethod, emptyError)) {

    document.listErrorMessages(List[String](emptyError))
  }

}
