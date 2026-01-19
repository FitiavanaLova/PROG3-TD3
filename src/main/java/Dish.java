import java.util.List;
import java.util.Objects;

public class Dish {
    private Integer id;
    private String name;
    private DishTypeEnum dishType;
    private Double sellingPrice;  // Prix de vente (peut être null)
    private List<Ingredient> ingredients;

    // Constructeurs
    public Dish() {
    }

    public Dish(Integer id, String name, DishTypeEnum dishType, Double sellingPrice) {
        this.id = id;
        this.name = name;
        this.dishType = dishType;
        this.sellingPrice = sellingPrice;
    }

    public Dish(Integer id, String name, DishTypeEnum dishType, Double sellingPrice, List<Ingredient> ingredients) {
        this.id = id;
        this.name = name;
        this.dishType = dishType;
        this.sellingPrice = sellingPrice;
        this.ingredients = ingredients;
    }

    // Getters et Setters
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

    public DishTypeEnum getDishType() {
        return dishType;
    }

    public void setDishType(DishTypeEnum dishType) {
        this.dishType = dishType;
    }

    public Double getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(Double sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    // Méthode getPrice() maintenue pour compatibilité (renvoie sellingPrice)
    public Double getPrice() {
        return sellingPrice;
    }

    // Méthode setPrice() maintenue pour compatibilité
    public void setPrice(Double price) {
        this.sellingPrice = price;
    }

    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<Ingredient> ingredients) {
        this.ingredients = ingredients;
        
        // Mettre à jour la référence au plat pour chaque ingrédient
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                ingredient.setDish(this);
            }
        }
    }

    // Méthode pour calculer le coût total du plat
    public Double getDishCost() {
        if (ingredients == null || ingredients.isEmpty()) {
            return 0.0;
        }
        
        double totalCost = 0.0;
        for (Ingredient ingredient : ingredients) {
            Double price = ingredient.getPrice();
            Double quantity = ingredient.getQuantity();
            
            // Vérifier que le prix et la quantité sont disponibles
            if (price == null) {
                throw new RuntimeException("Price is null for ingredient: " + ingredient.getName());
            }
            
            if (quantity == null) {
                throw new RuntimeException("Quantity is null for ingredient: " + ingredient.getName());
            }
            
            // Ajouter au coût total : prix * quantité
            totalCost += price * quantity;
        }
        
        return totalCost;
    }

    // Méthode pour calculer la marge brute
    public Double getGrossMargin() {
        if (sellingPrice == null) {
            throw new RuntimeException("Selling price is null for dish: " + name);
        }
        
        Double cost = getDishCost();
        if (cost == null) {
            throw new RuntimeException("Could not calculate dish cost for: " + name);
        }
        
        return sellingPrice - cost;
    }

    // Méthode pour obtenir le pourcentage de marge
    public Double getGrossMarginPercentage() {
        if (sellingPrice == null || sellingPrice == 0.0) {
            throw new RuntimeException("Selling price is null or zero for dish: " + name);
        }
        
        Double margin = getGrossMargin();
        return (margin / sellingPrice) * 100;
    }

    // Méthode pour ajouter un ingrédient
    public void addIngredient(Ingredient ingredient) {
        if (ingredients == null) {
            ingredients = new java.util.ArrayList<>();
        }
        
        ingredient.setDish(this);
        ingredients.add(ingredient);
    }

    // Méthode pour supprimer un ingrédient par ID
    public boolean removeIngredientById(Integer ingredientId) {
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        
        return ingredients.removeIf(ingredient -> 
            ingredient.getId() != null && ingredient.getId().equals(ingredientId)
        );
    }

    // Méthode pour trouver un ingrédient par ID
    public Ingredient findIngredientById(Integer ingredientId) {
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }
        
        for (Ingredient ingredient : ingredients) {
            if (ingredient.getId() != null && ingredient.getId().equals(ingredientId)) {
                return ingredient;
            }
        }
        
        return null;
    }

    // Méthode pour vérifier si le plat contient un ingrédient spécifique
    public boolean containsIngredient(String ingredientName) {
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        
        for (Ingredient ingredient : ingredients) {
            if (ingredientName.equalsIgnoreCase(ingredient.getName())) {
                return true;
            }
        }
        
        return false;
    }

    // Méthode pour obtenir le nombre d'ingrédients
    public int getIngredientCount() {
        return ingredients == null ? 0 : ingredients.size();
    }

    // Méthode pour obtenir un résumé du plat
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Plat: ").append(name)
               .append(" (").append(dishType).append(")\n")
               .append("Prix de vente: ");
        
        if (sellingPrice != null) {
            summary.append(sellingPrice).append("€");
        } else {
            summary.append("Non défini");
        }
        
        summary.append("\nCoût: ");
        try {
            Double cost = getDishCost();
            summary.append(cost).append("€");
        } catch (RuntimeException e) {
            summary.append("Impossible à calculer");
        }
        
        summary.append("\nIngrédients (").append(getIngredientCount()).append("):\n");
        
        if (ingredients != null && !ingredients.isEmpty()) {
            for (Ingredient ingredient : ingredients) {
                summary.append("  - ").append(ingredient.getName())
                       .append(" (").append(ingredient.getQuantity())
                       .append(" ").append(ingredient.getUnit() != null ? ingredient.getUnit() : "UNIT")
                       .append(") - ").append(ingredient.getPrice()).append("€\n");
            }
        } else {
            summary.append("  Aucun ingrédient\n");
        }
        
        if (sellingPrice != null) {
            try {
                Double margin = getGrossMargin();
                Double marginPercent = getGrossMarginPercentage();
                summary.append("Marge brute: ").append(margin).append("€ (")
                       .append(String.format("%.1f", marginPercent)).append("%)");
            } catch (RuntimeException e) {
                summary.append("Marge brute: Impossible à calculer");
            }
        }
        
        return summary.toString();
    }

    // Méthodes equals, hashCode et toString
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Dish dish = (Dish) o;
        return Objects.equals(id, dish.id) &&
               Objects.equals(name, dish.name) &&
               dishType == dish.dishType &&
               Objects.equals(sellingPrice, dish.sellingPrice) &&
               Objects.equals(ingredients, dish.ingredients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, dishType, sellingPrice, ingredients);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dish{")
          .append("id=").append(id)
          .append(", name='").append(name).append('\'')
          .append(", dishType=").append(dishType)
          .append(", sellingPrice=").append(sellingPrice)
          .append(", ingredients=[");
        
        if (ingredients != null) {
            for (int i = 0; i < ingredients.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(ingredients.get(i).getName())
                  .append("(").append(ingredients.get(i).getQuantity())
                  .append(" ").append(ingredients.get(i).getUnit())
                  .append(")");
            }
        }
        
        sb.append("]}")
          .append(", dishCost=");
        
        try {
            sb.append(getDishCost());
        } catch (RuntimeException e) {
            sb.append("ERROR");
        }
        
        sb.append('}');
        
        return sb.toString();
    }

    // Méthode pour formater le plat pour l'affichage
    public String toFormattedString() {
        return String.format("""
            Plat #%d: %s
            Type: %s
            Prix de vente: %s
            Nombre d'ingrédients: %d
            Coût total: %s€
            """,
            id,
            name,
            dishType,
            sellingPrice != null ? sellingPrice + "€" : "Non défini",
            getIngredientCount(),
            getDishCost()
        );
    }

    // Méthode pour obtenir une représentation JSON simplifiée
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{")
            .append("\"id\":").append(id)
            .append(",\"name\":\"").append(name).append("\"")
            .append(",\"dishType\":\"").append(dishType).append("\"")
            .append(",\"sellingPrice\":").append(sellingPrice)
            .append(",\"dishCost\":").append(getDishCost());
        
        if (sellingPrice != null) {
            try {
                json.append(",\"grossMargin\":").append(getGrossMargin());
            } catch (RuntimeException e) {
                json.append(",\"grossMargin\":null");
            }
        }
        
        json.append(",\"ingredients\":[");
        
        if (ingredients != null && !ingredients.isEmpty()) {
            for (int i = 0; i < ingredients.size(); i++) {
                if (i > 0) json.append(",");
                json.append(ingredients.get(i).toJson());
            }
        }
        
        json.append("]}");
        return json.toString();
    }

    // Méthode statique pour créer un plat avec validation
    public static Dish createDish(Integer id, String name, DishTypeEnum dishType, 
                                 Double sellingPrice, List<Ingredient> ingredients) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du plat ne peut pas être vide");
        }
        
        if (dishType == null) {
            throw new IllegalArgumentException("Le type de plat ne peut pas être null");
        }
        
        Dish dish = new Dish();
        dish.setId(id);
        dish.setName(name.trim());
        dish.setDishType(dishType);
        dish.setSellingPrice(sellingPrice);
        
        if (ingredients != null) {
            dish.setIngredients(ingredients);
        }
        
        return dish;
    }

    // Méthode pour cloner un plat
    public Dish clone() {
        Dish clone = new Dish();
        clone.id = this.id;
        clone.name = this.name;
        clone.dishType = this.dishType;
        clone.sellingPrice = this.sellingPrice;
        
        if (this.ingredients != null) {
            // Note: Ceci crée une nouvelle liste mais partage les mêmes objets Ingredient
            clone.ingredients = new java.util.ArrayList<>(this.ingredients);
        }
        
        return clone;
    }

    // Méthode pour vérifier si le plat est valide (pour la sauvegarde)
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        if (dishType == null) {
            return false;
        }
        
        // Vérifier que tous les ingrédients ont une quantité si présents
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                if (ingredient.getQuantity() == null) {
                    return false;
                }
            }
        }
        
        return true;
    }
}