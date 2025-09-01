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
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.agent.actions.IdentifierAction
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.StreamlineIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.StreamlineIncomeSourceForm.nextIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.requests.agent.IdentifierRequest
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{No, Yes}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.MultipleSelfEmploymentsService
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.agent.NextIncomeSource
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FullIncomeSourceController @Inject()(identify: IdentifierAction,
                                           nextIncomeSource: NextIncomeSource,
                                           mcc: MessagesControllerComponents,
                                           multipleSelfEmploymentsService: MultipleSelfEmploymentsService,
                                           appConfig: AppConfig)
                                          (implicit val ec: ExecutionContext)
  extends FrontendController(mcc) with I18nSupport {

  def show(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = identify.async { implicit request =>
    multipleSelfEmploymentsService.fetchStreamlineBusiness(request.reference, id) map {
      case Right(streamlineBusiness) =>
        Ok(view(
          nextIncomeSourceForm = nextIncomeSourceForm.bind(StreamlineIncomeSourceForm.createIncomeSourceData(
            maybeTradeName = streamlineBusiness.trade,
            maybeBusinessName = streamlineBusiness.name,
            maybeStartDate = streamlineBusiness.startDate,
            maybeStartDateBeforeLimit = streamlineBusiness.startDateBeforeLimit,
            maybeAccountingMethod = None
          )).discardingErrors,
          id = id,
          isEditMode = isEditMode,
          isGlobalEdit = isGlobalEdit
        ))
      case Left(_) =>
        throw new InternalServerException(s"[FullIncomeSourceController][show] - Unexpected error when fetching streamline business details")
    }
  }

  def submit(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = identify.async { implicit request =>
    nextIncomeSourceForm.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(view(
          formWithErrors, id, isEditMode, isGlobalEdit
        ))),
      {
        case (trade, name, startDateBeforeLimit) =>
          saveDataAndContinue(
            id = id,
            trade = trade,
            name = name,
            startDateBeforeLimit = startDateBeforeLimit match {
              case Yes => true
              case No => false
            },
            isEditMode = isEditMode,
            isGlobalEdit = isGlobalEdit
          )
      }
    )
  }

  private def saveDataAndContinue(id: String,
                                  trade: String,
                                  name: String,
                                  startDateBeforeLimit: Boolean,
                                  isEditMode: Boolean,
                                  isGlobalEdit: Boolean)(implicit request: IdentifierRequest[_]): Future[Result] = {
    multipleSelfEmploymentsService.saveStreamlinedIncomeSource(
      reference = request.reference,
      businessId = id,
      trade = trade,
      name = name,
      startDateBeforeLimit = startDateBeforeLimit,
      accountingMethod = None
    ) map {
      case Right(_) =>
        if (!startDateBeforeLimit) {
          Redirect(routes.BusinessStartDateController.show(id, isEditMode, isGlobalEdit))
        } else if (isEditMode || isGlobalEdit) {
          Redirect(routes.SelfEmployedCYAController.show(id, isEditMode, isGlobalEdit))
        } else {
          Redirect(routes.AddressLookupRoutingController.checkAddressLookupJourney(id, isEditMode))
        }
      case Left(_) =>
        throw new InternalServerException("[FullIncomeSourceController][submit] - Could not save next income source")
    }
  }

  def backUrl(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): String = {
    if (isEditMode || isGlobalEdit) routes.SelfEmployedCYAController.show(id, isEditMode, isGlobalEdit = isGlobalEdit).url
    else appConfig.clientYourIncomeSourcesUrl
  }

  private def view(nextIncomeSourceForm: Form[_], id: String, isEditMode: Boolean, isGlobalEdit: Boolean)
                  (implicit request: IdentifierRequest[AnyContent]): Html =
    nextIncomeSource(
      nextIncomeSourceForm = nextIncomeSourceForm,
      postAction = routes.FullIncomeSourceController.submit(id, isEditMode, isGlobalEdit),
      backUrl = backUrl(id, isEditMode, isGlobalEdit),
      isEditMode = isEditMode,
      clientDetails = request.clientDetails
    )

}
