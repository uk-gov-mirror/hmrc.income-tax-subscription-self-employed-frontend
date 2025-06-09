package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models

case class NameAndTradeModel(
  reference: String,
  id: String,
  name: String,
  trade: String,
  isAgent: Boolean
)
