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
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.DuplicatesController
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils.ReferenceRetrieval
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.StreamlineIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.agent.StreamlineIncomeSourceForm.nextIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{ClientDetails, No, Yes}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.MultipleSelfEmploymentsService.SaveSelfEmploymentDataDuplicates
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.{AuthService, ClientDetailsRetrieval, MultipleSelfEmploymentsService, SessionDataService}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ImplicitDateFormatter
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.agent.NextIncomeSource
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.language.LanguageUtils

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NextIncomeSourceController @Inject()(nextIncomeSource: NextIncomeSource,
                                           clientDetailsRetrieval: ClientDetailsRetrieval,
                                           mcc: MessagesControllerComponents,
                                           multipleSelfEmploymentsService: MultipleSelfEmploymentsService,
                                           authService: AuthService)
                                          (val sessionDataService: SessionDataService,
                                           val languageUtils: LanguageUtils,
                                           val appConfig: AppConfig)
                                          (implicit val ec: ExecutionContext)
  extends FrontendController(mcc) with ReferenceRetrieval with I18nSupport with ImplicitDateFormatter with FeatureSwitching {

  def show(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withAgentReference { reference =>
        clientDetailsRetrieval.getClientDetails flatMap { clientDetails =>
          multipleSelfEmploymentsService.fetchStreamlineBusiness(reference, id) map {
            case Right(streamlineBusiness) =>
              if (streamlineBusiness.isFirstBusiness) {
                Redirect(routes.FirstIncomeSourceController.show(id, isEditMode, isGlobalEdit))
              } else {
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
                  clientDetails = clientDetails,
                  isGlobalEdit
                ))
              }
            case Left(_) =>
              throw new InternalServerException(s"[NextIncomeSourceController][show] - Unexpected error, fetching streamline business details")
          }
        }
      }
    }
  }

  def submit(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withAgentReference { reference =>
        nextIncomeSourceForm.bindFromRequest().fold(
          formWithErrors =>
            clientDetailsRetrieval.getClientDetails map { clientDetails =>
              BadRequest(view(
                formWithErrors, id, isEditMode, clientDetails, isGlobalEdit
              ))
            },
          {
            case (trade, name, startDateBeforeLimit) =>
              saveDataAndContinue(
                reference = reference,
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
    }
  }

  private def saveDataAndContinue(reference: String,
                                  id: String,
                                  trade: String,
                                  name: String,
                                  startDateBeforeLimit: Boolean,
                                  isEditMode: Boolean,
                                  isGlobalEdit: Boolean)(implicit hc: HeaderCarrier): Future[Result] = {
    multipleSelfEmploymentsService.saveStreamlinedIncomeSource(
      reference = reference,
      businessId = id,
      trade = trade,
      name = name,
      startDateBeforeLimit = startDateBeforeLimit,
      accountingMethod = None
    ) flatMap  {
      case Right(_) => Future.successful(
        if (!startDateBeforeLimit) {
          Redirect(routes.BusinessStartDateController.show(id, isEditMode, isGlobalEdit))
        } else if (isEditMode || isGlobalEdit) {
          Redirect(routes.SelfEmployedCYAController.show(id, isEditMode, isGlobalEdit))
        } else {
          Redirect(routes.AddressLookupRoutingController.checkAddressLookupJourney(id, isEditMode))
        })
      case Left(SaveSelfEmploymentDataDuplicates) =>
        DuplicatesController.duplicatesFound(
          multipleSelfEmploymentsService,
          reference,
          id,
          trade,
          name,
          isAgent = true
        )
      case Left(_) =>
        throw new InternalServerException("[FullIncomeSourceController][submit] - Could not save sole trader full income source")
    }
  }

  def backUrl(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): String = {

    if (isEditMode || isGlobalEdit) routes.SelfEmployedCYAController.show(id, isEditMode, isGlobalEdit = isGlobalEdit).url
    else appConfig.clientYourIncomeSourcesUrl
  }

  private def view(nextIncomeSourceForm: Form[_], id: String,
                   isEditMode: Boolean, clientDetails: ClientDetails, isGlobalEdit: Boolean)
                  (implicit request: Request[AnyContent]): Html =
    nextIncomeSource(
      nextIncomeSourceForm = nextIncomeSourceForm,
      postAction = routes.NextIncomeSourceController.submit(id, isEditMode, isGlobalEdit),
      backUrl = backUrl(id, isEditMode, isGlobalEdit),
      isEditMode = isEditMode,
      clientDetails = clientDetails
    )

}



