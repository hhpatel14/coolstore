-- Add sequences required by Hibernate for entity ID generation
create sequence if not exists ORDER_ITEMS_SEQ start with 1 increment by 50;
create sequence if not exists ORDERS_SEQ start with 1 increment by 50;
