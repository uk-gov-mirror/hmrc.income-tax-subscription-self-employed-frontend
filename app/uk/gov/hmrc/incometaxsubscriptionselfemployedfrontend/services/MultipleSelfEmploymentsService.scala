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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services

import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.SelfEmploymentDataKeys
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.IncomeTaxSubscriptionConnector
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.GetSelfEmploymentsFailure
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccess
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.agent.StreamlineBusiness
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.MultipleSelfEmploymentsService.{SaveSelfEmploymentDataDuplicates, SaveSelfEmploymentDataError, SaveSelfEmploymentDataFailure}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MultipleSelfEmploymentsService @Inject()(applicationCrypto: ApplicationCrypto,
                                               incomeTaxSubscriptionConnector: IncomeTaxSubscriptionConnector)
                                              (implicit ec: ExecutionContext) {

  implicit val jsonCrypto: Encrypter with Decrypter = applicationCrypto.JsonCrypto

  def fetchSoleTraderBusinesses(reference: String)
                               (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[SoleTraderBusinesses]]] = {
    incomeTaxSubscriptionConnector.getSubscriptionDetails[SoleTraderBusinesses](
      reference = reference,
      id = SelfEmploymentDataKeys.soleTraderBusinessesKey
    )(implicitly, SoleTraderBusinesses.encryptedFormat)
  }

  def saveSoleTraderBusinesses(reference: String, soleTraderBusinesses: SoleTraderBusinesses)
                              (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] =
    if (soleTraderBusinesses.hasDuplicates) {
      Future.successful(Left(SaveSelfEmploymentDataDuplicates))
    } else {
      incomeTaxSubscriptionConnector.saveSubscriptionDetails[SoleTraderBusinesses](
        reference = reference,
        id = SelfEmploymentDataKeys.soleTraderBusinessesKey,
        data = soleTraderBusinesses
      )(implicitly, SoleTraderBusinesses.encryptedFormat) flatMap {
        case Right(value) =>
          incomeTaxSubscriptionConnector.deleteSubscriptionDetails(
            reference = reference,
            key = SelfEmploymentDataKeys.incomeSourcesComplete
          ) map {
            case Right(_) => Right(value)
            case Left(_) => Left(SaveSelfEmploymentDataFailure)
          }
        case Left(_) =>
          Future.successful(Left(SaveSelfEmploymentDataFailure))
      }
    }

  private def findData[T](reference: String, id: String, modelToData: SoleTraderBusiness => Option[T])
                         (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[T]]] = {
    fetchSoleTraderBusinesses(reference) map { result =>
      result map {
        case Some(soleTraderBusinesses) => soleTraderBusinesses.businesses
        case None => Seq.empty[SoleTraderBusiness]
      } map { businesses =>
        businesses.find(_.id == id).flatMap(modelToData)
      }
    }
  }

  def fetchStreamlineBusiness(reference: String, id: String)
                             (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, StreamlineBusiness]] = {
    fetchSoleTraderBusinesses(reference) map { result =>
      result flatMap {
        case Some(SoleTraderBusinesses(businesses, maybeAccountingMethod)) =>
          businesses.find(_.id == id) match {
            case None => getNameAndTrade(reference, id).map {
              case Right(nameAndTrade) =>
                StreamlineBusiness(
                  trade = nameAndTrade.map(_.trade),
                  name = nameAndTrade.map(_.name),
                  startDate = None,
                  startDateBeforeLimit = None,
                  accountingMethod = maybeAccountingMethod,
                  isFirstBusiness = false
                )
              case Left(_) => Future.successful(
                StreamlineBusiness(None, None, None, None, None, isFirstBusiness = true)
              )
            }
            case Some(maybeFirstBusiness) =>
              Right(StreamlineBusiness(
                trade = maybeFirstBusiness.trade,
                name = maybeFirstBusiness.name,
                startDate = maybeFirstBusiness.startDate,
                startDateBeforeLimit = maybeFirstBusiness.startDateBeforeLimit,
                accountingMethod = maybeAccountingMethod,
                isFirstBusiness = false
              ))
          })
        case None => Right(
          StreamlineBusiness(None, None, None, None, None, isFirstBusiness = true)
        )
      }
    }
  }

  def fetchBusiness(reference: String, id: String)
                   (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[SoleTraderBusiness]]] = {
    fetchSoleTraderBusinesses(reference) map { result =>
      result map {
        case Some(SoleTraderBusinesses(businesses, _)) =>
          businesses.find(_.id == id)
        case None => None
      }
    }
  }

  private def saveData(reference: String,
                       id: String,
                       businessUpdate: SoleTraderBusiness => SoleTraderBusiness,
                       accountingMethod: Option[AccountingMethod] = None)
                      (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {

    def updateSoleTraderBusinesses(soleTraderBusinesses: SoleTraderBusinesses): SoleTraderBusinesses = {
      val updatedBusinessesList: Seq[SoleTraderBusiness] = if (soleTraderBusinesses.businesses.exists(_.id == id)) {
        soleTraderBusinesses.businesses map {
          case business if business.id == id => businessUpdate(business)
          case business => business
        }
      } else {
        soleTraderBusinesses.businesses :+ businessUpdate(SoleTraderBusiness(id = id))
      }

      soleTraderBusinesses.copy(businesses = updatedBusinessesList)
    }

    fetchSoleTraderBusinesses(reference) map { result =>
      result map {
        case Some(soleTraderBusinesses) => soleTraderBusinesses
        case None => SoleTraderBusinesses(businesses = Seq.empty[SoleTraderBusiness])
      } map updateSoleTraderBusinesses
    } flatMap {
      case Right(soleTraderBusinesses) if accountingMethod.isDefined =>
        saveSoleTraderBusinesses(reference, soleTraderBusinesses.copy(accountingMethod = accountingMethod))
      case Right(soleTraderBusinesses) =>
        saveSoleTraderBusinesses(reference, soleTraderBusinesses)
      case Left(_) => Future.successful(Left(SaveSelfEmploymentDataFailure))
    }

  }

  def fetchStartDate(reference: String, businessId: String)
                    (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[DateModel]]] = {
    findData[DateModel](reference, businessId, _.startDate)
  }

  def saveStartDate(reference: String, businessId: String, startDate: DateModel)
                   (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {
    saveData(reference, businessId, _.copy(startDate = Some(startDate), confirmed = false))
  }

  def saveName(reference: String, businessId: String, name: String)
              (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {
    saveData(reference, businessId, _.copy(name = Some(name), confirmed = false))
  }

  def saveTrade(reference: String, businessId: String, trade: String)
               (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {
    saveData(reference, businessId, _.copy(trade = Some(trade), confirmed = false))
  }

  def saveAddress(reference: String, businessId: String, address: Address)
                 (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {
    saveData(reference, businessId, _.copy(address = Some(address), confirmed = false))
  }

  def confirmBusiness(reference: String, businessId: String)
                     (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {
    saveData(reference, businessId, _.copy(confirmed = true))
  }

  def saveStreamlinedIncomeSource(reference: String,
                       businessId: String,
                       trade: String,
                       name: String,
                       startDateBeforeLimit: Boolean,
                       accountingMethod: Option[AccountingMethod])
                      (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {
    saveData(
      reference = reference,
      id = businessId,
      businessUpdate = _.copy(name = Some(name), trade = Some(trade), startDateBeforeLimit = Some(startDateBeforeLimit), confirmed = false),
      accountingMethod = accountingMethod
    )
  }

  def fetchAccountingMethod(reference: String)(implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[AccountingMethod]]] = {
    fetchSoleTraderBusinesses(reference) map {
      case Right(soleTraderBusinesses) => Right(soleTraderBusinesses.flatMap(_.accountingMethod))
      case Left(value) => Left(value)
    }
  }

  def saveAccountingMethod(reference: String, businessId: String, accountingMethod: AccountingMethod)
                          (implicit hc: HeaderCarrier): Future[Either[SaveSelfEmploymentDataError, PostSubscriptionDetailsSuccess]] = {

    fetchSoleTraderBusinesses(reference) map { result =>
      result map {
        case Some(soleTraderBusinesses) =>
          soleTraderBusinesses.copy(accountingMethod = Some(accountingMethod),
            businesses = soleTraderBusinesses.businesses.map {
              case business if business.id == businessId => business.copy(confirmed = false)
              case business => business
            }
          )
        case None => SoleTraderBusinesses(Seq.empty[SoleTraderBusiness], accountingMethod = Some(accountingMethod))
      }
    } flatMap {
      case Right(businesses) => saveSoleTraderBusinesses(reference, businesses)
      case Left(_) => Future.successful(Left(SaveSelfEmploymentDataFailure))
    }
  }

  def fetchFirstAddress(reference: String)
                       (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[Address]]] = {
    fetchSoleTraderBusinesses(reference) map { result =>
      result.map { maybeBusinesses =>
        maybeBusinesses.flatMap { soleTraderBusinesses =>
          soleTraderBusinesses.businesses.flatMap(_.address).headOption
        }
      }
    }
  }

  def fetchFirstBusinessName(reference: String)
                            (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[String]]] = {
    fetchSoleTraderBusinesses(reference) map { result =>
      result.map { maybeBusinesses =>
        maybeBusinesses.flatMap { soleTraderBusinesses =>
          soleTraderBusinesses.businesses.flatMap(_.name).headOption
        }
      }
    }
  }

  def fetchAllNameTradeCombos(reference: String)
                             (implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Seq[(String, Option[String], Option[String])]]] = {
    fetchSoleTraderBusinesses(reference) map { result =>
      result map {
        case Some(soleTraderBusinesses) => soleTraderBusinesses.businesses.map(business => (business.id, business.name, business.trade))
        case None => Seq.empty[(String, Option[String], Option[String])]
      }
    }
  }

  def saveNameAndTrade(
    model: NameAndTradeModel
  )(implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Boolean]] = {
    Future.successful(Right(true))
  }

  def getNameAndTrade(
    reference: String,
    id: String
  )(implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[NameAndTradeModel]]] = {
    Future.successful(Right(Some(NameAndTradeModel(reference = reference, id = id, name = "", trade = "", isAgent = false))))
  }
}

object MultipleSelfEmploymentsService {

  abstract class SaveSelfEmploymentDataError

  case object SaveSelfEmploymentDataFailure extends SaveSelfEmploymentDataError

  case object SaveSelfEmploymentDataDuplicates extends SaveSelfEmploymentDataError

}
