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

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import play.twirl.api.Html
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.IncomeTaxSubscriptionConnector
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.BusinessTradeNameForm._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.FormUtil._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.BusinessTradeNameModel
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.AuthService
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.business_trade_name
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class BusinessTradeNameController @Inject()(mcc: MessagesControllerComponents,
                                            incomeTaxSubscriptionConnector: IncomeTaxSubscriptionConnector,
                                            authService: AuthService)
                                           (implicit val ec: ExecutionContext, val appConfig: AppConfig)
  extends FrontendController(mcc) with I18nSupport {

  def view(businessTradeNameForm: Form[BusinessTradeNameModel])(implicit request: Request[AnyContent]): Html =
    business_trade_name(
      businessTradeNameForm = businessTradeNameForm,
      postAction = uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.routes.BusinessTradeNameController.submit(),
      backUrl = backUrl()
    )


  def show(): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      incomeTaxSubscriptionConnector.getSelfEmployments[BusinessTradeNameModel](BusinessTradeNameController.businessTradeNameKey).map {
        case Right(businessTradeName) =>
          Ok(view(businessTradeNameValidationForm.fill(businessTradeName)))
        case error => throw new InternalServerException(error.toString)
      }
    }
  }


  def submit(): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      businessTradeNameValidationForm.bindFromRequest.fold(
        formWithErrors =>
          Future.successful(BadRequest(view(formWithErrors))),
        businessTradeName =>
          incomeTaxSubscriptionConnector.saveSelfEmployments(BusinessTradeNameController.businessTradeNameKey, businessTradeName) map (_ =>
            Redirect(uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.routes.BusinessAccountingMethodController.show())
            )
      )
    }
  }

  def backUrl(): String =
    uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.routes.BusinessNameController.show().url

}

object BusinessTradeNameController {
  val businessTradeNameKey: String = "BusinessTradeName"
}
