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
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.SelfEmploymentDataKeys.businessesKey
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.IncomeTaxSubscriptionConnector
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.{InvalidJson, UnexpectedStatusFailure}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{AddAnotherBusinessModel, No, SelfEmploymentData, Yes}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.AddAnotherBusinessForm._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.AuthService
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ImplicitDateFormatterImpl
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.check_your_answers
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessListCYAController @Inject()(authService: AuthService,
                                          incomeTaxSubscriptionConnector: IncomeTaxSubscriptionConnector,
                                          mcc: MessagesControllerComponents)
                                         (implicit val ec: ExecutionContext, val appConfig: AppConfig, dateFormatter: ImplicitDateFormatterImpl)
  extends FrontendController(mcc) with I18nSupport {

  def view(addAnotherBusinessForm: Form[AddAnotherBusinessModel], businesses: Seq[SelfEmploymentData], isEditMode: Boolean)
          (implicit request: Request[AnyContent]): Html = {
    check_your_answers(
      addAnotherBusinessForm = addAnotherBusinessForm,
      answers = businesses,
      postAction = uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.routes.BusinessListCYAController.submit(isEditMode),
      isEditMode,
      implicitDateFormatter = dateFormatter
    )
  }

  def show(isEditMode: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      incomeTaxSubscriptionConnector.getSelfEmployments[Seq[SelfEmploymentData]](businessesKey).map {
        case Right(Some(businesses)) if businesses.exists(_.isComplete) =>
          Ok(view(addAnotherBusinessForm(businesses.size, appConfig.limitOnNumberOfBusinesses), businesses.filter(_.isComplete), isEditMode))
        case Right(_) => Redirect(routes.InitialiseController.initialise())
        case Left(UnexpectedStatusFailure(status)) =>
          throw new InternalServerException(s"[BusinessListCYAController][show] - getSelfEmployments connection failure, status: $status")
        case Left(InvalidJson) =>
          throw new InternalServerException("[BusinessListCYAController][show] - Invalid Json")
      }
    }
  }

  def submit(isEditMode: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      incomeTaxSubscriptionConnector.getSelfEmployments[Seq[SelfEmploymentData]](businessesKey).map {
        case Right(Some(businesses)) if businesses.exists(_.isComplete) =>
          addAnotherBusinessForm(businesses.size, appConfig.limitOnNumberOfBusinesses).bindFromRequest.fold(
            formWithErrors => BadRequest(view(formWithErrors, businesses, isEditMode)),
            businessNameData => businessNameData.addAnotherBusiness match {
              case Yes => Redirect(routes.InitialiseController.initialise())
              case No =>
                if (isEditMode) {
                  Redirect(appConfig.incomeTaxSubscriptionFrontendBaseUrl + "/check-your-answers")
              } else {
                Redirect(routes.BusinessAccountingMethodController.show())
              }
            }
          )
        case Right(_) => Redirect(routes.InitialiseController.initialise())
        case Left(UnexpectedStatusFailure(status)) =>
          throw new InternalServerException(s"[BusinessListCYAController][submit] - getSelfEmployments connection failure, status: $status")
        case Left(InvalidJson) =>
          throw new InternalServerException("[BusinessListCYAController][submit] - Invalid Json")
      }
    }
  }
}