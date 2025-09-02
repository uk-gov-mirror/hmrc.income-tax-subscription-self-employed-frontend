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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.individual

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import play.twirl.api.Html
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.DuplicatesController
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils.ReferenceRetrieval
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.forms.individual.StreamlineIncomeSourceForm
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.agent.StreamlineBusiness
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{DuplicateDataModel, No, Yes}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.MultipleSelfEmploymentsService.SaveSelfEmploymentDataDuplicates
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.{AuthService, DuplicateDataService, MultipleSelfEmploymentsService, SessionDataService}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.ImplicitDateFormatter
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.individual.FullIncomeSource
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.language.LanguageUtils

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FullIncomeSourceController @Inject()(fullIncomeSource: FullIncomeSource,
                                           mcc: MessagesControllerComponents,
                                           multipleSelfEmploymentsService: MultipleSelfEmploymentsService,
                                           duplicateDataService: DuplicateDataService,
                                           authService: AuthService)
                                          (val sessionDataService: SessionDataService,
                                           val languageUtils: LanguageUtils,
                                           val appConfig: AppConfig)
                                          (implicit val ec: ExecutionContext)
  extends FrontendController(mcc) with ReferenceRetrieval with I18nSupport with ImplicitDateFormatter with FeatureSwitching {

  def show(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withIndividualReference { reference =>
        fetchBusiness(reference, id) map { streamlineBusiness =>
          val form: Form[_] = StreamlineIncomeSourceForm.fullIncomeSourceForm
          Ok(view(
            fullIncomeSourceForm = form.bind(StreamlineIncomeSourceForm.createIncomeSourceData(
              maybeTradeName = streamlineBusiness.trade,
              maybeBusinessName = streamlineBusiness.name,
              maybeStartDate = streamlineBusiness.startDate,
              maybeStartDateBeforeLimit = streamlineBusiness.startDateBeforeLimit
            )).discardingErrors,
            id = id,
            isEditMode = isEditMode,
            isGlobalEdit = isGlobalEdit,
            isFirstBusiness = streamlineBusiness.isFirstBusiness
          ))
        }
      }
    }
  }

  def submit(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withIndividualReference { reference =>
        StreamlineIncomeSourceForm.fullIncomeSourceForm.bindFromRequest().fold(
          formWithErrors => fetchBusiness(reference, id) map { streamlineBusiness =>
            BadRequest(view(formWithErrors, id, isEditMode, isGlobalEdit, streamlineBusiness.isFirstBusiness))
          }, {
            case (trade, name, startDateBeforeLimit) =>
              multipleSelfEmploymentsService.saveStreamlinedIncomeSource(
                reference = reference,
                businessId = id,
                trade = trade,
                name = name,
                startDateBeforeLimit = startDateBeforeLimit match {
                  case Yes => true
                  case No => false
                },
                accountingMethod = None
              ) flatMap {
                case Right(_) => Future.successful(
                  if (startDateBeforeLimit == No) {
                    Redirect(routes.BusinessStartDateController.show(id, isEditMode, isGlobalEdit))
                  } else if (isEditMode || isGlobalEdit) {
                    Redirect(routes.SelfEmployedCYAController.show(id, isEditMode, isGlobalEdit))
                  } else {
                    Redirect(routes.AddressLookupRoutingController.checkAddressLookupJourney(id, isEditMode))
                  })
                case Left(SaveSelfEmploymentDataDuplicates) =>
                  DuplicatesController.duplicatesFound(
                    duplicateDataService,
                    reference,
                    id,
                    trade,
                    name,
                    isAgent = false
                  )
                case Left(_) =>
                  throw new InternalServerException("[FullIncomeSourceController][submit] - Could not save sole trader full income source")
              }
          }
        )
      }
    }
  }

  private def fetchBusiness(reference: String, id: String)(implicit hc: HeaderCarrier): Future[StreamlineBusiness] = {
    multipleSelfEmploymentsService.fetchStreamlineBusiness(reference, id) map {
      case Right(streamlineBusiness) => streamlineBusiness
      case Left(_) =>
        throw new InternalServerException("[FullIncomeSourceController][fetchBusiness] - Could not fetch streamline business details")
    }
  }

  private def view(fullIncomeSourceForm: Form[_], id: String,
                   isEditMode: Boolean, isGlobalEdit: Boolean, isFirstBusiness: Boolean)
                  (implicit request: Request[AnyContent]): Html =
    fullIncomeSource(
      fullIncomeSourceForm = fullIncomeSourceForm,
      postAction = routes.FullIncomeSourceController.submit(id, isEditMode, isGlobalEdit),
      backUrl = backUrl(id, isEditMode, isGlobalEdit, isFirstBusiness),
      isEditMode = isEditMode
    )

  def backUrl(id: String, isEditMode: Boolean, isGlobalEdit: Boolean, isFirstBusiness: Boolean): String = {
    if (isEditMode || isGlobalEdit) {
      routes.SelfEmployedCYAController.show(id, isEditMode, isGlobalEdit).url
    } else if (isEnabled(RemoveAccountingMethod)) {
      appConfig.yourIncomeSourcesUrl
    } else if (isFirstBusiness) {
      routes.BusinessAccountingMethodController.show(id).url
    } else {
      appConfig.yourIncomeSourcesUrl
    }
  }

}
