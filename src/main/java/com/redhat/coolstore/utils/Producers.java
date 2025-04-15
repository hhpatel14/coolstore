package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Named;
import java.util.logging.Logger;

public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    @Named
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}