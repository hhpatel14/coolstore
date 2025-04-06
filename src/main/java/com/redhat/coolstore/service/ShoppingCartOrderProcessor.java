package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import io.smallrye.reactive.messaging.annotations.Broadcast;

@RequestScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Broadcast
    @Channel("orders")
    Emitter<String> ordersEmitter;
  
    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }



}