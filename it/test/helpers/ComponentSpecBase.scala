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

package helpers

import connectors.stubs.SessionDataConnectorStub.stubGetSessionData
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.scalatestplus.play.{PlaySpec, PortNumber}
import play.api.http.HeaderNames
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsString, OFormat}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.StreamlineIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.individual.{StreamlineIncomeSourceForm => IndividualStreamlineIncomeSourceForm, _}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ITSASessionKeys.REFERENCE

import java.time.LocalDate

trait ComponentSpecBase extends PlaySpec with CustomMatchers with GuiceOneServerPerSuite
  with WiremockHelper with BeforeAndAfterAll with BeforeAndAfterEach
  with GivenWhenThen with SessionCookieBaker {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(config)
    .build()

  implicit lazy val crypto: Encrypter with Decrypter = app.injector.instanceOf[ApplicationCrypto].JsonCrypto
  implicit lazy val soleTraderBusinessesFormat: OFormat[SoleTraderBusinesses] = SoleTraderBusinesses.encryptedFormat

  implicit def ws(implicit app: Application): WSClient = app.injector.instanceOf[WSClient]

  val titleSuffix = " - Use software to send Income Tax updates - GOV.UK"
  val agentTitleSuffix = " - Use software to report your client’s Income Tax - GOV.UK"
  val reference: String = "test-reference"

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: String = WiremockHelper.wiremockPort.toString
  val mockUrl: String = s"http://$mockHost:$mockPort"

  implicit val messages: Messages = app.injector.instanceOf[MessagesApi].preferred(FakeRequest())

  override lazy val cookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  def config: Map[String, String] = Map(
    "auditing.enabled" -> "false",
    "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
    "microservice.services.auth.host" -> mockHost,
    "microservice.services.auth.port" -> mockPort,
    "microservice.services.base.host" -> mockHost,
    "microservice.services.base.port" -> mockPort,
    "microservice.services.des.url" -> mockUrl,
    "microservice.services.income-tax-subscription.host" -> mockHost,
    "microservice.services.income-tax-subscription.port" -> mockPort,
    "microservice.services.income-tax-subscription-stubs.host" -> mockHost,
    "microservice.services.income-tax-subscription-stubs.port" -> mockPort,
    "microservice.services.address-lookup-frontend.host" -> mockHost,
    "microservice.services.address-lookup-frontend.port" -> mockPort
  )

  override def beforeAll(): Unit = {
    startWiremock()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    resetWiremock()
    super.beforeEach()

    stubGetSessionData(REFERENCE)(OK, JsString(reference))
  }

  def get(uri: String, additionalCookies: Map[String, String] = Map.empty)(implicit ws: WSClient, portNumber: PortNumber): WSResponse = {
    await(
      buildClient(uri)
        .withHttpHeaders(HeaderNames.COOKIE -> bakeSessionCookie(additionalCookies))
        .get()
    )
  }

  def getWithHeaders(uri: String, headers: (String, String)*): WSResponse = {
    await(
      buildClient(uri)
        .withHttpHeaders(headers: _*)
        .get()
    )
  }

  def post(uri: String, additionalCookies: Map[String, String] = Map.empty)
          (body: Map[String, Seq[String]]): WSResponse = {
    await(
      buildClient(uri)
        .withHttpHeaders(
          HeaderNames.COOKIE -> bakeSessionCookie(additionalCookies), "Csrf-Token" -> "nocheck"
        )
        .post(body)
    )
  }

  val baseUrl: String = "/report-quarterly/income-and-expenses/sign-up/self-employments"

  def signOut: WSResponse = get("/logout")

  private def buildClient(path: String)(implicit ws: WSClient, portNumber: PortNumber): WSRequest =
    ws.url(s"http://localhost:${portNumber.value}$baseUrl$path").withFollowRedirects(false)


  def getBusinessStartDate(id: String): WSResponse = get(s"/details/business-start-date?id=$id")

  def getClientBusinessStartDate(id: String): WSResponse = get(s"/client/details/business-start-date?id=$id")

  def submitClientBusinessStartDate(id: String, request: Option[DateModel], inEditMode: Boolean = false, isGlobalEdit: Boolean = false): WSResponse = {
    val uri = s"/client/details/business-start-date?id=$id&isEditMode=$inEditMode&isGlobalEdit=$isGlobalEdit"
    post(uri)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.BusinessStartDateForm.businessStartDateForm(
            LocalDate.now(), LocalDate.now(), d => d.toString
          ).fill(model).data.map {
            case (k, v) =>
              (k, Seq(v))
          }
      )
    )
  }

  def submitBusinessStartDate(request: Option[DateModel], id: String, inEditMode: Boolean = false, isGlobalEdit: Boolean = false): WSResponse = {
    val uri = s"/details/business-start-date?id=$id&isEditMode=$inEditMode&isGlobalEdit=$isGlobalEdit"
    post(uri)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.individual.BusinessStartDateForm.businessStartDateForm(
            BusinessStartDateForm.maxStartDate, _.toString
          ).fill(model).data.map {
            case (k, v) =>
              (k, Seq(v))
          }
      )
    )
  }

  def getInitialise: WSResponse = get(s"/details")

  def getClientInitialise: WSResponse = get(s"/client/details")

  def getBusinessNameConfirmation(id: String)(session: Map[String, String] = Map.empty[String, String]): WSResponse = {
    get(s"/details/confirm-business-name?id=$id", session)
  }

  def submitBusinessNameConfirmation(id: String, request: Option[YesNo])(session: Map[String, String] = Map.empty[String, String]): WSResponse = {
    post(s"/details/confirm-business-name?id=$id", session)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          BusinessNameConfirmationForm.businessNameConfirmationForm.fill(model).data.map { case (k, v) => (k, Seq(v)) }
      )
    )
  }

  def getBusinessAddressConfirmation(id: String)(session: Map[String, String] = Map.empty[String, String]): WSResponse = {
    get(s"/details/confirm-business-address?id=$id", session)
  }

  def submitBusinessAddressConfirmation(id: String, request: Option[YesNo])(session: Map[String, String] = Map.empty[String, String]): WSResponse = {
    post(s"/details/confirm-business-address?id=$id", session)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          BusinessAddressConfirmationForm.businessAddressConfirmationForm.fill(model).data.map { case (k, v) => (k, Seq(v)) }
      )
    )
  }

  def getClientBusinessAddressConfirmation(id: String)(session: Map[String, String] = Map.empty[String, String]): WSResponse = {
    get(s"/client/details/confirm-business-address?id=$id", session)
  }

  def submitClientBusinessAddressConfirmation(id: String, request: Option[YesNo])(session: Map[String, String] = Map.empty[String, String]): WSResponse = {
    post(s"/client/details/confirm-business-address?id=$id", session)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          BusinessAddressConfirmationForm.businessAddressConfirmationForm.fill(model).data.map { case (k, v) => (k, Seq(v)) }
      )
    )
  }

  def getBusinessName(id: String): WSResponse = get(s"/details/business-name?id=$id")

  def submitBusinessName(id: String, inEditMode: Boolean, isGlobalEdit: Boolean, request: Option[String]): WSResponse = {
    val uri = s"/details/business-name?id=$id&isEditMode=$inEditMode&isGlobalEdit=$isGlobalEdit"
    post(uri)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          BusinessNameForm.businessNameValidationForm(Nil).fill(model).data.map { case (k, v) => (k, Seq(v)) }
      )
    )
  }

  def getBusinessTradeName(id: String): WSResponse = get(s"/details/business-trade?id=$id")

  def submitBusinessTradeName(id: String, inEditMode: Boolean, isGlobalEdit: Boolean, request: Option[String]): WSResponse = {
    val uri = s"/details/business-trade?id=$id&isEditMode=$inEditMode&isGlobalEdit=$isGlobalEdit"
    post(uri)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          BusinessTradeNameForm.businessTradeNameValidationForm(Nil).fill(model).data.map {
            case (k, v) =>
              (k, Seq(v))
          }
      )
    )
  }

  def getTimeout: WSResponse = get(uri = "/timeout")

  def getClientTimeout: WSResponse = get(uri = "/client/timeout")

  def getKeepAlive: WSResponse = get(uri = "/keep-alive")

  def getClientKeepAlive: WSResponse = get(uri = "/client/keep-alive")


  def getBusinessAccountingMethod(id: String, inEditMode: Boolean = false): WSResponse = get(s"/details/business-accounting-method?id=$id&isEditMode=$inEditMode")

  def submitBusinessAccountingMethod(request: Option[AccountingMethod],
                                     inEditMode: Boolean = false,
                                     isGlobalEdit: Boolean = false,
                                     id: String): WSResponse = {
    val uri = s"/details/business-accounting-method?isEditMode=$inEditMode&id=$id&isGlobalEdit=$isGlobalEdit"
    post(uri)(
      request.fold(Map.empty[String, Seq[String]])(
        model =>
          BusinessAccountingMethodForm.businessAccountingMethodForm.fill(model).data.map {
            case (k, v) =>
              (k, Seq(v))
          }
      )
    )
  }

  def getChangeAccountingMethod(id: String): WSResponse = get(s"/details/change-accounting-method?id=$id")

  def submitChangeAccountingMethod(id: String): WSResponse = post(s"/details/change-accounting-method?id=$id")(Map.empty)

  def getBusinessCheckYourAnswers(id: String, isEditMode: Boolean): WSResponse = get(s"/details/business-check-your-answers?id=$id,isEditMode=$isEditMode")

  def submitBusinessCheckYourAnswers(id: String, isGlobalEdit: Boolean): WSResponse = {
    post(s"/details/business-check-your-answers?id=$id&isGlobalEdit=$isGlobalEdit")(Map.empty)
  }

  def getClientBusinessCheckYourAnswers(id: String, isEditMode: Boolean): WSResponse = get(s"/client/details/business-check-your-answers?id=$id,isEditMode=$isEditMode")

  def submitClientBusinessCheckYourAnswers(id: String, isGlobalEdit: Boolean): WSResponse = {
    post(s"/client/details/business-check-your-answers?id=$id&isGlobalEdit=$isGlobalEdit")(Map.empty)
  }

  def getAddressLookupInitialise(businessId: String): WSResponse = get(s"/address-lookup-initialise/$businessId")

  def getAddressLookup(businessId: String, id: String, isEditMode: Boolean = false): WSResponse =
    get(s"/details/address-lookup/$businessId?id=$id&isEditMode=$isEditMode")

  def getClientAddressLookupInitialise(itsaId: String): WSResponse = get(s"/client/address-lookup-initialise/$itsaId")

  def getClientAddressLookup(itsaId: String, id: String, isEditMode: Boolean = false): WSResponse = get(s"/client/details/address-lookup/$itsaId?id=$id")

  def getFirstIncomeSource(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): WSResponse = {
    get(s"/client/details/initial-sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")
  }

  def submitFirstIncomeSource(trade: Option[String],
                              name: Option[String],
                              startDate: Option[DateModel],
                              startDateBeforeLimit: Option[Boolean],
                              accountingMethod: Option[AccountingMethod],
                              id: String,
                              isEditMode: Boolean,
                              isGlobalEdit: Boolean): WSResponse = {
    post(s"/client/details/initial-sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")(
      StreamlineIncomeSourceForm.createIncomeSourceData(trade, name, startDate, startDateBeforeLimit, accountingMethod)
        .map { case (k, v) => (k, Seq(v)) }
    )
  }

  def getNextIncomeSource(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): WSResponse = {
    get(s"/client/details/subsequent-sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")
  }

  def submitNextIncomeSource(trade: Option[String],
                             name: Option[String],
                             startDate: Option[DateModel],
                             startDateBeforeLimit: Option[Boolean],
                             id: String,
                             isEditMode: Boolean,
                             isGlobalEdit: Boolean): WSResponse = {
    post(s"/client/details/subsequent-sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")(
      StreamlineIncomeSourceForm.createIncomeSourceData(trade, name, startDate, startDateBeforeLimit, None)
        .map { case (k, v) => (k, Seq(v)) }
    )
  }

  def getClientFullIncomeSource(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): WSResponse = {
    get(s"/client/details/sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")
  }

  def submitClientFullIncomeSource(trade: Option[String],
                                   name: Option[String],
                                   startDate: Option[DateModel],
                                   startDateBeforeLimit: Option[Boolean],
                                   id: String,
                                   isEditMode: Boolean,
                                   isGlobalEdit: Boolean): WSResponse = {
    post(s"/client/details/sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")(
      StreamlineIncomeSourceForm.createIncomeSourceData(trade, name, startDate, startDateBeforeLimit, None)
        .map { case (k, v) => (k, Seq(v)) }
    )
  }

  def getFullIncomeSource(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): WSResponse = {
    get(s"/details/sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")
  }

  def submitFullIncomeSource(trade: Option[String],
                             name: Option[String],
                             startDateBeforeLimit: Option[Boolean],
                             id: String,
                             isEditMode: Boolean,
                             isGlobalEdit: Boolean): WSResponse = {
    post(s"/details/sole-trader-business?id=$id&isEditMode=$isEditMode&isGlobalEdit=$isGlobalEdit")(
      IndividualStreamlineIncomeSourceForm.createIncomeSourceData(trade, name, None, startDateBeforeLimit)
        .map { case (k, v) => (k, Seq(v)) }
    )

  }

  def removeHtmlMarkup(stringWithMarkup: String): String =
    stringWithMarkup.replaceAll("<.+?>", " ").replaceAll("[\\s]{2,}", " ").trim

}
