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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.scalatest.{Assertion, MustMatchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.Call
import play.api.test.FakeRequest
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig

import scala.collection.JavaConversions._

trait ViewSpec extends WordSpec with MustMatchers with GuiceOneAppPerSuite {

  implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  implicit lazy val mockMessages: Messages = messagesApi.preferred(FakeRequest())

  val testBackUrl = "/test-back-url"
  val testCall = Call("POST", "/test-url")

  implicit class ElementTest(element: Element) {

    val content: Element = element.getElementsByTag("article").head

    val getParagraphs: Elements = element.getElementsByTag("p")

    val getBulletPoints: Elements = element.getElementsByTag("li")

    val getH1Element: Elements = element.getElementsByTag("h1")

    val getH2Elements: Elements = element.getElementsByTag("h2")

    val getFormElements: Elements = element.getElementsByClass("form-field-group")

    val getErrorSummaryMessage: Elements = element.select("#error-summary-display ul")

    val getErrorSummary: Elements = element.select("#error-summary-display")

    val getSubmitButton: Elements = element.select("button[type=submit]")

    val getHintText: String = element.select(s"""[class=form-hint]""").text()

    val getForm: Elements = element.select("form")

    val getBackLink: Elements = element.select(s"a[class=back-link]")

    def getParagraphNth(index: Int = 0): String = {
      element.select("p").get(index).text()
    }

    def getBulletPointNth(index: Int = 0): String = element.select("ul[class=bullets] li").get(index).text()

    def getRadioButtonByIndex(index: Int = 0): Element = element.select("div .multiple-choice").get(index)

    def getSpan(id: String): Elements = element.select(s"""span[id=$id]""")

    def getLink(id: String): Elements = element.select(s"""a[id=$id]""")

    def getTextFieldInput(id: String): Elements = element.select(s"""input[id=$id]""")

    def getFieldErrorMessage(id: String): Elements = element.select(s"""a[id=$id-error-summary]""")

    def mustHaveTextField(name: String, label: String): Assertion = {
      val eles = element.select(s"input[name=$name]")
      if (eles.isEmpty) fail(s"$name does not have an input field with name=$name\ncurrent list of inputs:\n[${element.select("input")}]")
      if (eles.size() > 1) fail(s"$name have multiple input fields with name=$name")
      val ele = eles.head
      ele.attr("type") mustBe "text"
      element.select(s"label[for=$name]").text() mustBe label
    }

    def listErrorMessages(errors: List[String]): Assertion = {
      errors.zipWithIndex.map {
        case (error, index) => element.select(s"span.error-notification:nth-child(${index + 1})").text mustBe error
      } forall (_ == succeed) mustBe true
    }

    def mustHaveDateField(id: String, legend: String, exampleDate: String, error: Option[String] = None): Assertion = {
      val ele = element.getElementById(id)
      ele.select("span.form-label-bold").text() mustBe legend
      ele.select("span.form-hint").text() mustBe exampleDate
      ele.tag().toString mustBe "fieldset"
      mustHaveTextField(s"$id.dateDay", "Day")
      mustHaveTextField(s"$id.dateMonth", "Month")
      mustHaveTextField(s"$id.dateYear", "Year")
      error.map { message =>
        ele.select("legend").select(".error-notification").attr("role") mustBe "tooltip"
        ele.select("legend").select(".error-notification").text mustBe message
      }.getOrElse(succeed)
    }

    def mustHavePara(paragraph: String): Assertion = {
      element.getElementsByTag("p").text() must include(paragraph)
    }

    def mustHaveErrorSummary(errors: List[String]): Assertion = {
      getErrorSummary.attr("class") mustBe "flash error-summary error-summary--show"
      getErrorSummary.attr("role") mustBe "alert"
      getErrorSummary.attr("aria-labelledby") mustBe "error-summary-heading"
      getErrorSummary.attr("tabindex") mustBe "-1"
      getErrorSummary.select("h2").attr("id") mustBe "error-summary-heading"
      getErrorSummary.select("h2").text mustBe "There’s a problem"
      getErrorSummary.select("ul > li").text mustBe errors.mkString(" ")
    }

  }

}
