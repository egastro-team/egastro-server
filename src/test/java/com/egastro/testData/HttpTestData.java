package com.egastro.testData;

public final class HttpTestData {
    public static final String PRODUCT_JSON = """
            {"name":" Milk ","description":" Fresh milk ","categoryId":2,"price":2.49,
             "unitOfMeasure":"L","imageUrl":"https://example.com/milk.png","available":true}
            """;
    public static final String CATEGORY_JSON = "{\"name\":\"Dairy\"}";
    public static final String PADDED_CATEGORY_JSON = "{\"name\":\" Dairy \"}";
    public static final String LOGIN_JSON = "{\"email\":\"user@example.com\",\"password\":\"Password123\"}";
    public static final String REGISTER_WITH_ROLE_JSON = "{\"email\":\" user@example.com \",\"password\":\"Password123\",\"role\":\"ADMIN\"}";
    public static final String ADD_TWO_ITEMS_JSON = "{\"productId\":3,\"quantity\":2}";
    public static final String ADD_ONE_ITEM_JSON = "{\"productId\":3,\"quantity\":1}";
    public static final String QUANTITY_TWO_JSON = "{\"quantity\":2}";
    public static final String QUANTITY_ONE_JSON = "{\"quantity\":1}";
    public static final String EMPTY_JSON = "{}";

    private HttpTestData() {
    }

    public static String productWithUnknownUnitJson() {
        return PRODUCT_JSON.replace("\"L\"", "\"INVALID\"");
    }
}
