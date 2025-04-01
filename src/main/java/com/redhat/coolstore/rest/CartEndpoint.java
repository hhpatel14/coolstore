package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ShoppingCartService;

@SessionScoped
@Path("/cart")
@Consumes(MediaType.APPLICATION_JSON)
public class CartEndpoint implements Serializable {

	private static final long serialVersionUID = -7227732980791688773L;

	@Inject
	private ShoppingCartService shoppingCartService;

	@GET
	@Path("/{cartId}")
	public ShoppingCart getCart(@PathParam("cartId") String cartId) {
		return shoppingCartService.getShoppingCart(cartId);
	}

	@POST
	@Path("/{cartId}/checkout")
	public ShoppingCart checkout(@PathParam("cartId") String cartId) {
		return shoppingCartService.checkOutShoppingCart(cartId);
	}

	@POST
	@Path("/{cartId}/items")
	public ShoppingCart add(@PathParam("cartId") String cartId, ShoppingCartItem item) {
		ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);
		cart.addShoppingCartItem(item);
		shoppingCartService.priceShoppingCart(cart);
		return cart;
	}

	@PUT
	@Path("/{cartId}/items")
	public ShoppingCart set(@PathParam("cartId") String cartId, List<ShoppingCartItem> items) {
		ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);
		cart.setShoppingCartItemList(items);
		shoppingCartService.priceShoppingCart(cart);
		return cart;
	}

	@DELETE
	@Path("/{cartId}")
	public ShoppingCart delete(@PathParam("cartId") String cartId) {
		shoppingCartService.deleteShoppingCart(cartId);
		return shoppingCartService.getShoppingCart(cartId);
	}

	private List<ShoppingCartItem> dedupeCartItems(List<ShoppingCartItem> cartItems) {
		List<ShoppingCartItem> result = new ArrayList<>();
		Map<String, Integer> quantityMap = new HashMap<>();
		for (ShoppingCartItem sci : cartItems) {
			if (quantityMap.containsKey(sci.getProduct().getItemId())) {
				quantityMap.put(sci.getProduct().getItemId(), quantityMap.get(sci.getProduct().getItemId()) + sci.getQuantity());
			} else {
				quantityMap.put(sci.getProduct().getItemId(), sci.getQuantity());
			}
		}

		for (String itemId : quantityMap.keySet()) {
			Product p = shoppingCartService.getProduct(itemId);
			ShoppingCartItem newItem = new ShoppingCartItem();
			newItem.setQuantity(quantityMap.get(itemId));
			newItem.setPrice(p.getPrice());
			newItem.setProduct(p);
			result.add(newItem);
		}
		return result;
	}
}
