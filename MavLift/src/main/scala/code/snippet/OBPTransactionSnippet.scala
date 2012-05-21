/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License. 

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
		by 
		Simon Redfern : simon AT tesobe DOT com
		Everett Sochowski: everett AT tesobe DOT com

 */
package code.snippet

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat
import net.liftweb.http._
import java.util.Calendar
import code.model.OBPTransaction
import code.model.OBPEnvelope
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import scala.xml.Text
import net.liftweb.common.{Box, Failure, Empty, Full}
import java.util.Date
import code.model.OBPAccount
import code.model.OBPAccount.{APublicAlias, APrivateAlias}
import net.liftweb.http.js.JsCmds.Noop
import code.model._

class OBPTransactionSnippet {

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""
  
  //Show all transactions from every account, for now
  val qry = QueryBuilder.start().get
  val envelopesToDisplay = OBPEnvelope.findAll(qry)
  
  val FORBIDDEN = "---"

  val view = S.uri match {
    case uri if uri.endsWith("authorities") => Authorities
    case uri if uri.endsWith("board") => Board
    case uri if uri.endsWith("our-network") => OurNetwork
    case uri if uri.endsWith("team") => Team
    //case uri if uri.endsWith("my-view") => "my-view" a solution has to be found for the editing case
    case _ => Anonymous
  }	
  
  val owner = TesobeBankAccountOwner.account
  val bankAccount = TesobeBankAccount.bankAccount
  val transactions = bankAccount.transactions
  val filteredTransactions = transactions.map(view.moderate(_))
  
  def displayAll = {
    def orderByDateDescending = (t1: FilteredTransaction, t2: FilteredTransaction) => {
      val date1 = t1.finishDate getOrElse new Date()
      val date2 = t2.finishDate getOrElse new Date()
      date1.after(date2)
    }
    
    val sortedTransactions = groupByDate(filteredTransactions.toList.sort(orderByDateDescending))
    
    "* *" #> sortedTransactions.map( transactionsForDay => {
     daySummary(transactionsForDay)
    })
  }


  def individualTransaction(transaction: FilteredTransaction): CssSel = {
   

    //TODO: discuss if we really need public/private disctinction when displayed,
    //it confuses more than it really informs the user of much
    //the view either shows more data or not, only that it is an alias is really informative
    def aliasRelatedInfo: CssSel = {
      transaction.alias match{
        case Public =>
          ".alias_indicator [class+]" #> "alias_indicator_public" &
            ".alias_indicator *" #> "(Alias)"
        case Private =>
          ".alias_indicator [class+]" #> "alias_indicator_private" &
            ".alias_indicator *" #> "(Alias)"
        case _ => NOOP_SELECTOR

      } 
    }

    def otherPartyInfo: CssSel = {

      //The extra information about the other party in the transaction
      

        def moreInfoBlank =
          ".other_account_more_info" #> NodeSeq.Empty &
            ".other_account_more_info_br" #> NodeSeq.Empty

        def moreInfoNotBlank =
          ".other_account_more_info *" #> transaction.moreInfo.toString() 
          

        def logoBlank =
          NOOP_SELECTOR

        def logoNotBlank =
          ".other_account_logo_img [src]" #> transaction.imageUrl

        def websiteBlank =
          ".other_acc_link" #> NodeSeq.Empty & //If there is no link to display, don't render the <a> element
            ".other_acc_link_br" #> NodeSeq.Empty

        def websiteNotBlank =
          ".other_acc_link [href]" #> transaction.url

        def openCorporatesBlank =
          ".open_corporates_link" #> NodeSeq.Empty

        def openCorporatesNotBlank =
          ".open_corporates_link [href]" #> transaction.openCorporatesUrl

        ".narrative *" #> {
          transaction.ownerComment match{
	          case Some(o) => o
	          case _ => None
        }} &//displayNarrative(env) &
          {
            transaction.moreInfo match{
              case Some(m) => if(m == "") moreInfoBlank else moreInfoNotBlank
              case _ => moreInfoBlank
            }
          } &
          {
            transaction.imageUrl match{
              case Some(i) => if(i == "") logoBlank else logoNotBlank
              case _ => logoBlank
            }
          } &
          {
            transaction.url match{
              case Some(m) => if(m == "") websiteBlank else websiteNotBlank
              case _ => websiteBlank
            }
          } &
          {
            transaction.openCorporatesUrl match{
              case Some(m) => if(m == "") openCorporatesBlank else openCorporatesNotBlank
              case _ => openCorporatesBlank
            }
          }

    }

    def commentsInfo = {
      {
        //If we're not allowed to see comments, don't show the comments section
        
        if (env.mediated_obpComments(consumer).isEmpty) ".comments *" #> ""
        else NOOP_SELECTOR
      } &
        ".comments_ext [href]" #> { consumer + "/transactions/" + envelopeID + "/comments" } &
        ".comment *" #> env.mediated_obpComments(consumer).getOrElse(Nil).size &
        ".symbol *" #> { if (amount.startsWith("-")) "-" else "+" } &
        ".out [class]" #> { if (amount.startsWith("-")) "out" else "in" }
    }
    
    ".the_name *" #> transaction.alias.toString() &
    ".amount *" #> { "€" + transaction.amount.toString().stripPrefix("-") } & //TODO: Format this number according to locale
    aliasRelatedInfo &
    otherPartyInfo &
    commentsInfo
  }
  
//  def editableNarrative(envelope: OBPEnvelope) = {
//    var narrative = envelope.narrative.get
//
//    CustomEditable.editable(narrative, SHtml.text(narrative, narrative = _), () => {
//      //save the narrative
//      envelope.narrative(narrative).save
//      Noop
//    }, "Narrative")
//  }
//
//  def displayNarrative(envelope: OBPEnvelope): NodeSeq = {
//    consumer match {
//      case "my-view" => editableNarrative(envelope)
//      case _ => Text(envelope.mediated_narrative(consumer).getOrElse(FORBIDDEN))
//    }
//  }

  def hasSameDate(t1: FilteredTransaction, t2: FilteredTransaction): Boolean = {

    val date1 = t1.finishDate getOrElse new Date()
    val date2 = t2.finishDate getOrElse new Date()
    
    val cal1 = Calendar.getInstance();
    val cal2 = Calendar.getInstance();
    cal1.setTime(date1);
    cal2.setTime(date2);
    
    //True if the two dates fall on the same day of the same year
    cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                  cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
  }

  /**
   * Splits a list of Transactions into a list of lists, where each of these new lists
   * is for one day.
   *
   * Example:
   * 	input : List(Jan 5,Jan 6,Jan 7,Jan 7,Jan 8,Jan 9,Jan 9,Jan 9,Jan 10)
   * 	output: List(List(Jan 5), List(Jan 6), List(Jan 7,Jan 7),
   * 				 List(Jan 8), List(Jan 9,Jan 9,Jan 9), List(Jan 10))
   */
  def groupByDate(list: List[FilteredTransaction]): List[List[FilteredTransaction]] = {
    list match {
      case Nil => Nil
      case h :: Nil => List(list)
      case h :: t => {
        //transactions that are identical to the head of the list
        val matches = list.filter(hasSameDate(h, _))
        List(matches) ++ groupByDate(list diff matches)
      }
    }
  }

  def formatDate(date: Box[Date]): String = {
    val dateFormat = new SimpleDateFormat("MMMM dd, yyyy")
    date match {
      case Full(d) => dateFormat.format(d)
      case _ => FORBIDDEN
    }
  }
  
  def daySummary(transactionsForDay: List[FilteredTransaction]) = {
    val aTransaction = transactionsForDay.last
   
    ".date *" #> {
      aTransaction.finishDate match{
        case Some(d) => d.toLocaleString()
        case _ => ""
      }
    }  &
      ".balance_number *" #> { "€" + {aTransaction.balance match{
        case Some(b) => b.toString
        case _ => ""
      } }} & //TODO: support other currencies, format the balance according to locale
      ".transaction_row *" #> transactionsForDay.map(t => individualTransaction(t))
  }
  
  //Fake it for now
  def accountDetails = {
    "#accountName *" #> "TESOBE / Music Pictures Ltd. Account (Postbank)"
  }
   
}

