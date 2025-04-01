package com.redhat.coolstore.service;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartService implements Serializable {

    private static final long serialVersionUID = -8378172743965708556L;

    private ConcurrentHashMap<String, ShoppingCart> carts = new ConcurrentHashMap<>();

    @Inject
    Logger log;

    @Inject
    ProductService productServices;

    @Inject
    PromoService ps;

    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    @Inject
    ShippingService shippingService;

    public ShoppingCartService() {
    }

    public ShoppingCart getShoppingCart(String cartId) {
        if (!carts.containsKey(cartId)) {
            carts.put(cartId, new ShoppingCart());
        }
        return carts.get(cartId);
    }

    public ShoppingCart checkOutShoppingCart(String cartId) {
        ShoppingCart cart = this.getShoppingCart(cartId);
      
        log.info("Sending order: ");
        shoppingCartOrderProcessor.process(cart);
   
        cart.resetShoppingCartItemList();
        priceShoppingCart(cart);
        carts.remove(cartId);
        return cart;
    }

    public void deleteShoppingCart(String cartId) {
        ShoppingCart cart = this.getShoppingCart(cartId);
        cart.resetShoppingCartItemList();
        priceShoppingCart(cart);
        carts.remove(cartId);
    }

    public void priceShoppingCart(ShoppingCart sc) {
        if (sc != null) {
            initShoppingCartForPricing(sc);

            if (sc.getShoppingCartItemList() != null && sc.getShoppingCartItemList().size() > 0) {
                ps.applyCartItemPromotions(sc);

                for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
                    sc.setCartItemPromoSavings(
                            sc.getCartItemPromoSavings() + sci.getPromoSavings() * sci.getQuantity());
                    sc.setCartItemTotal(sc.getCartItemTotal() + sci.getPrice() * sci.getQuantity());
                }

                sc.setShippingTotal(shippingService.calculateShipping(sc));

                if (sc.getCartItemTotal() >= 25) {
                    sc.setShippingTotal(sc.getShippingTotal()
                            + shippingService.calculateShippingInsurance(sc));
                }
            }

            ps.applyShippingPromotions(sc);
            sc.setCartTotal(sc.getCartItemTotal() + sc.getShippingTotal());
        }
    }

    private void initShoppingCartForPricing(ShoppingCart sc) {
        sc.setCartItemTotal(0);
        sc.setCartItemPromoSavings(0);
        sc.setShippingTotal(0);
        sc.setShippingPromoSavings(0);
        sc.setCartTotal(0);

        for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
            Product p = getProduct(sci.getProduct().getItemId());
            //if product exist
            if (p != null) {
                sci.setProduct(p);
                sci.setPrice(p.getPrice());
            }
            sci.setPromoSavings(0);
        }
    }

    public Product getProduct(String itemId) {
        return productServices.getProductByItemId(itemId);
    }
}
