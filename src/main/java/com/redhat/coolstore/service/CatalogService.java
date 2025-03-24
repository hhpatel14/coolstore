package com.redhat.coolstore.service;

import java.util.List;
import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import jakarta.persistence.EntityManager;

import com.redhat.coolstore.model.*;

@ApplicationScoped
public class CatalogService {

    @Inject
    Logger log;

    @Inject
    private EntityManager em;

    public CatalogService() {
        // Configure the logger to log to standard output
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.ALL);
        log.addHandler(consoleHandler);
        log.setUseParentHandlers(false);
    }

    public List<CatalogItemEntity> getCatalogItems() {
        log.info("Fetching catalog items");
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<CatalogItemEntity> criteria = cb.createQuery(CatalogItemEntity.class);
        Root<CatalogItemEntity> member = criteria.from(CatalogItemEntity.class);
        criteria.select(member);
        return em.createQuery(criteria).getResultList();
    }

    public CatalogItemEntity getCatalogItemById(String itemId) {
        log.info("Fetching catalog item by ID: " + itemId);
        return em.find(CatalogItemEntity.class, itemId);
    }

    public void updateInventoryItems(String itemId, int deducts) {
        log.info("Updating inventory items for item ID: " + itemId + " with deducts: " + deducts);
        InventoryEntity inventoryEntity = getCatalogItemById(itemId).getInventory();
        int currentQuantity = inventoryEntity.getQuantity();
        inventoryEntity.setQuantity(currentQuantity - deducts);
        em.merge(inventoryEntity);
    }
}