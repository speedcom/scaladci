package scaladci
package examples
import org.specs2.mutable.Specification

/*
Shopping cart example (version 4c) - no Roles!!

No role methods, no interaction between objects (except with the cart).

No DCI anymore - only procedural algorithms in the Context which could be
any class now (notice it's not extending Context anymore).

Could we call it a Service now? If so, the specifications/requirements
below doesn't tell a story any more.

See discussion at:
https://groups.google.com/forum/?fromgroups=#!topic/object-composition/JJiLWBsZWu0

===========================================================================
shopping cart Service (disclaimer: don't know how to specify a Service...)

Specifications:
---------------------------------------------------------------------------
Add product to cart:
  - reserve product in warehouse
  - add item to order
  - show updated contents of cart to customer

Review order:
  - present cart with current items/prices to customer

Pay order:
  - confirm sufficient funds are available
  - initiate transfer of funds
  - confirm purchase to customer

Remove item from cart:
  - show updated cart to customer

Requirements
---------------------------------------------------------------------------
Product out of stock:
  - don't add item to cart
  - inform customer of shortage

customer has gold membership:
  - calculate discount on products

customer has insufficient funds to pay Order:
  - inform customer of insufficient funds on credit card
===========================================================================
*/

class ShoppingCart4c extends Specification {
  import ShoppingCartModel._

  // No DCI Context any longer - is it a "Service" now??
  class PlaceOrder(shop: Company, customer: Person) {
    // No Role any longer
    private val cart = Order(customer)

    // Service methods
    def customerMarksdesiredProductInshop(productId: Int): Option[Product] = {
      if (!shop.stock.isDefinedAt(productId))
        return None
      val product = shop.stock(productId)

      // get price with discount if any
      val customerIsGoldMember = shop.goldMembers.contains(customer)
      val goldMemberReduction = 0.5
      val discountFactor = if (customerIsGoldMember) goldMemberReduction else 1
      val discountedPrice = (product.price * discountFactor).toInt

      val desiredProduct = product.copy(price = discountedPrice)
      cart.items.put(productId, desiredProduct)
      Some(desiredProduct)
    }

    def customerRequestsToReviewOrder: Seq[(Int, Product)] = {
      cart.items.toIndexedSeq.sortBy(_._1)
    }

    def customerPaysOrder: Boolean = {
      val orderTotal = cart.items.map(_._2.price).sum
      if (orderTotal > customer.cash)
        return false

      customer.cash -= orderTotal
      shop.cash += orderTotal

      customer.owns ++= cart.items
      cart.items foreach (shop.stock remove _._1)
      true
    }

    def customerRemovesProductFromcart(productId: Int): Option[Product] = {
      if (!cart.items.isDefinedAt(productId))
        return None
      cart.items.remove(productId)
    }

    // (no role implementations!)
  }


  // Test various scenarios.
  // (copy and paste of ShoppingCart4a tests)

  "Main success scenario" in new ShoppingCart {

    // Initial status (same for all tests...)
    shop.stock === Map(tires, wax, bmw)
    shop.cash === 100000
    customer.cash === 20000
    customer.owns === Map()

    val order = new PlaceOrder(shop, customer)

    // customer wants wax and tires
    order.customerMarksdesiredProductInshop(p1)
    order.customerMarksdesiredProductInshop(p2)

    order.customerRequestsToReviewOrder === Seq(wax, tires)

    val orderCompleted = order.customerPaysOrder === true

    shop.stock === Map(bmw)
    shop.cash === 100000 + 40 + 600
    customer.cash === 20000 - 40 - 600
    customer.owns === Map(tires, wax)
  }

  "Product out of stock" in new ShoppingCart {

    // Wax out of stock
    shop.stock.remove(p1)
    shop.stock === Map(tires, bmw)

    val order = new PlaceOrder(shop, customer)

    // customer wants wax
    val itemAdded = order.customerMarksdesiredProductInshop(p1) === None
    order.customerRequestsToReviewOrder === Seq()

    order.customerMarksdesiredProductInshop(p2)

    val orderCompleted = order.customerPaysOrder === true

    shop.stock === Map(bmw)
    shop.cash === 100000 + 600
    customer.cash === 20000 - 600
    customer.owns === Map(tires)
  }

  "customer has gold membership" in new ShoppingCart {

    // customer is gold member
    shop.goldMembers.add(customer)
    shop.goldMembers.contains(customer) === true

    val order = new PlaceOrder(shop, customer)

    order.customerMarksdesiredProductInshop(p1)

    val discountedWax = 1 -> Product("Wax", (40 * 0.5).toInt)
    order.customerRequestsToReviewOrder === Seq(discountedWax)

    val orderCompleted = order.customerPaysOrder === true

    shop.stock === Map(tires, bmw)
    shop.cash === 100000 + 20
    customer.cash === 20000 - 20
    customer.owns === Map(discountedWax)
  }

  "customer has too low credit" in new ShoppingCart {

    val order = new PlaceOrder(shop, customer)

    // customer wants a BMW
    val itemAdded = order.customerMarksdesiredProductInshop(p3)

    // Any product is added - shop doesn't yet know if customer can afford it
    itemAdded === Some(bmw._2)
    order.customerRequestsToReviewOrder === Seq(bmw)

    // customer tries to pay order
    val paymentStatus = order.customerPaysOrder

    // shop informs customer of too low credit
    paymentStatus === false

    // customer removes unaffordable BMW from cart
    order.customerRemovesProductFromcart(p3)

    // customer aborts shopping and no purchases are made
    shop.stock === Map(tires, wax, bmw)
    shop.cash === 100000
    customer.cash === 20000
    customer.owns === Map()
  }

  "All deviations in play" in new ShoppingCart {

    // Tires out of stock
    shop.stock.remove(p2)
    shop.stock === Map(wax, bmw)

    // We have a gold member
    shop.goldMembers.add(customer)

    val order = new PlaceOrder(shop, customer)

    // Let's get some tires
    val tiresItemAdded = order.customerMarksdesiredProductInshop(p2)

    // Product out of stock!
    shop.stock.contains(p2) === false

    // Nothing added to order yet
    tiresItemAdded === None
    order.customerRequestsToReviewOrder === Seq()

    // Let's buy the BMW instead. As a gold member that should be possible!
    val bmwItemAdded = order.customerMarksdesiredProductInshop(p3)

    // Discounted BMW is added to order
    val discountedBMW = Product("BMW", (50000 * 0.5).toInt)
    bmwItemAdded === Some(discountedBMW)
    order.customerRequestsToReviewOrder === Seq(p3 -> discountedBMW)

    // Ouch! We couldn't afford it.
    val paymentAttempt1 = order.customerPaysOrder === false

    // It's still 5000 too much for us, even with the membership discount
    discountedBMW.price - customer.cash === 5000

    // Ok, no new car today
    order.customerRemovesProductFromcart(p3)

    // Order is back to empty
    order.customerRequestsToReviewOrder === Seq()

    // Let's get some wax anyway...
    val waxItemAdded = order.customerMarksdesiredProductInshop(p1)

    // Did we get our membership discount on this one?
    val discountedWax = Product("Wax", (40 * 0.5).toInt)
    waxItemAdded === Some(discountedWax)

    // Now we can afford it!
    val paymentAttempt2 = order.customerPaysOrder === true

    // Not much shopping done Today. At least we got some cheap wax.
    shop.stock === Map(bmw)
    shop.cash === 100000 + 20
    customer.cash === 20000 - 20
    customer.owns === Map(p1 -> discountedWax)
  }
}