/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils.ReferenceRetrieval
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.BusinessStartDateForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.BusinessStartDateForm.businessStartDateForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.utils.FormUtil._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{ClientDetails, DateModel, SoleTraderBusiness}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.{AuthService, ClientDetailsRetrieval, MultipleSelfEmploymentsService, SessionDataService}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ImplicitDateFormatter
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.agent.{BusinessStartDate => BusinessStartDateView}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.language.LanguageUtils

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessStartDateController @Inject()(mcc: MessagesControllerComponents,
                                            clientDetailsRetrieval: ClientDetailsRetrieval,
                                            multipleSelfEmploymentsService: MultipleSelfEmploymentsService,
                                            authService: AuthService,
                                            businessStartDate: BusinessStartDateView)
                                           (val sessionDataService: SessionDataService,
                                            val languageUtils: LanguageUtils,
                                            val appConfig: AppConfig)
                                           (implicit val ec: ExecutionContext)
  extends FrontendController(mcc) with ReferenceRetrieval with I18nSupport with ImplicitDateFormatter with FeatureSwitching {

  def view(businessStartDateForm: Form[DateModel], id: String, isEditMode: Boolean, isGlobalEdit: Boolean, clientDetails: ClientDetails, businessTrade: String)
          (implicit request: Request[AnyContent]): Html = {
    businessStartDate(
      businessStartDateForm = businessStartDateForm,
      postAction = routes.BusinessStartDateController.submit(id, isEditMode, isGlobalEdit),
      backUrl = backUrl(id, isEditMode, isGlobalEdit),
      clientDetails = clientDetails,
      businessTrade = businessTrade
    )
  }

  def show(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withAgentReference { reference =>
        multipleSelfEmploymentsService.fetchBusiness(reference, id) flatMap {
          case Right(Some(SoleTraderBusiness(_, _, _, maybeStartDate, _, Some(trade), _))) =>
            clientDetailsRetrieval.getClientDetails map { clientDetails =>
              Ok(view(
                businessStartDateForm = form.fill(maybeStartDate),
                id = id,
                isEditMode = isEditMode,
                isGlobalEdit = isGlobalEdit,
                clientDetails = clientDetails,
                businessTrade = trade
              ))
            }
          case Right(_) =>
            if (isEnabled(RemoveAccountingMethod)) {
              Future.successful(Redirect(routes.FullIncomeSourceController.show(id, isEditMode, isGlobalEdit)))
            } else {
              Future.successful(Redirect(routes.FirstIncomeSourceController.show(id, isEditMode, isGlobalEdit)))
            }
          case Left(error) =>
            throw new InternalServerException(s"[BusinessStartDateController][show] - ${error.toString}")
        }
      }
    }
  }

  def submit(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withAgentReference { reference =>
        form.bindFromRequest().fold(
          formWithErrors =>
            multipleSelfEmploymentsService.fetchBusiness(reference, id) flatMap {
              case Right(Some(SoleTraderBusiness(_, _, _, _, _, Some(trade), _))) =>
                clientDetailsRetrieval.getClientDetails map { clientDetails =>
                  BadRequest(view(formWithErrors, id, isEditMode, isGlobalEdit, clientDetails, trade))
                }
              case Right(_) =>
                Future.successful(Redirect(routes.FirstIncomeSourceController.show(id, isEditMode, isGlobalEdit)))
              case Left(error) =>
                throw new InternalServerException(error.toString)
            },
          businessStartDateData =>
            multipleSelfEmploymentsService.saveStartDate(reference, id, businessStartDateData) map {
              case Right(_) =>
                next(id, isEditMode, isGlobalEdit)
              case Left(_) =>
                throw new InternalServerException("[BusinessStartDateController][submit] - Could not save business start date")
            }
        )
      }
    }
  }

  private def next(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Result = Redirect(
    if (isEditMode || isGlobalEdit) {
      routes.SelfEmployedCYAController.show(id, isEditMode = isEditMode, isGlobalEdit)
    } else {
      routes.AddressLookupRoutingController.checkAddressLookupJourney(id)
    }
  )

  def backUrl(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): String = {
    if (isEnabled(RemoveAccountingMethod)) {
      routes.FullIncomeSourceController.show(id, isEditMode, isGlobalEdit).url
    } else {
      routes.FirstIncomeSourceController.show(id, isEditMode, isGlobalEdit).url
    }
  }

  def form(implicit request: Request[_]): Form[DateModel] = {
    businessStartDateForm(
      minStartDate = BusinessStartDateForm.minStartDate,
      maxStartDate = BusinessStartDateForm.maxStartDate,
      _.toLongDate()
    )
  }
}
