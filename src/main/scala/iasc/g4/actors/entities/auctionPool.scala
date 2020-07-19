package iasc.g4.actors.entities

import akka.actor.typed.ActorRef
import iasc.g4.models.AuctionInstance
import iasc.g4.models.Models.Command

object auctionPoolEntity{
  var auctionPool = Set[AuctionInstance]()

  def set(auctionPool:Set[AuctionInstance]): Unit ={
    this.auctionPool = auctionPool
  }

  def getAuctionActorById(id:String) : ActorRef[Command] = {
    var auctionAux = this.auctionPool.find(a => a.getId()==id)
    if (auctionAux==None)
      return null
    else
      return auctionAux.head.getAuction()
  }

  def freeAuction(id:String) = {
    var auctionInstances = this.auctionPool.find(a => a.getId()==id)
    if (auctionInstances!=None) {
      var auctionInstance = auctionInstances.head
      auctionInstance.setIsFree(true)
      auctionInstance.setId(null)
      printf("Se completó la subasta " + id)
    }
  }

  def getFreeAuctionActor(id:String,replyTo: ActorRef[String]) : ActorRef[Command] = {
    var auctionAux = getAuctionActorById(id)
    if (auctionAux==null) {
      var auctionInstances =this.auctionPool.find(a => a.getIsFree())
      if (auctionInstances == None) {
        replyTo ! "No hay instancias libres"
        return null
      } else {
        var auctionInstance = auctionInstances.head
        auctionInstance.setIsFree(false)
        auctionInstance.setId(id)
        return auctionInstance.getAuction()
      }
    } else {
      replyTo ! "Ya existe una subasta con Id "+id
      return null
    }
  }

  def getAuctionInstance(_index:Long,router:ActorRef[Command]) : AuctionInstance = {
    var auctionInstance : AuctionInstance = new AuctionInstance() {
      override var id: String = null
      override var isFree: Boolean = true
      override var auction: ActorRef[Command] = router
      override var index: Long = _index
    }
    return auctionInstance
  }

}
