import java.time.Instant;
import java.util.List;

public class Ingredient {

    // --- Champs principaux (table ingredient) ---
    private Integer id;
    private String name;
    private CategoryEnum category;
    private Double price;

    // --- Gestion des stocks ---
    private List<StockMovement> stockMovementList;

    // --- Champs utilisés dans dish_ingredient ---
    private Double quantity;   // quantité requise pour un plat
    private String unit;       // unité utilisée dans dish_ingredient

    // --- Constructeurs ---
    public Ingredient() {
    }

    public Ingredient(Integer id, String name, CategoryEnum category, Double price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    // --- Getters & Setters principaux ---
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CategoryEnum getCategory() {
        return category;
    }

    public void setCategory(CategoryEnum category) {
        this.category = category;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    // --- Stock movements ---
    public List<StockMovement> getStockMovementList() {
        return stockMovementList;
    }

    public void setStockMovementList(List<StockMovement> stockMovementList) {
        this.stockMovementList = stockMovementList;
    }

    // --- Dish_ingredient fields ---
    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    // -------------------------------------------------
    //  Calcul du niveau de stock à une date donnée
    // -------------------------------------------------
    public Double getStockValueAt(Instant t) {
        double stock = 0.0;

        if (stockMovementList == null || t == null) {
            return stock;
        }

        for (StockMovement sm : stockMovementList) {
            if (sm.getCreation_datetime().toInstant().isBefore(t)
                    || sm.getCreation_datetime().toInstant().equals(t)) {

                if (sm.getMouvement_Type() == Mouvement_TypeEnum.IN) {
                    stock += sm.getQuantity();
                } else {
                    stock -= sm.getQuantity();
                }
            }
        }
        return stock;
    }
}
