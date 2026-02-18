import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        DataRetriever dr = new DataRetriever();
        
        // Remplacer 1 par l'ID souhaité
        int ingredientId = 3;
        
        StockValue svDb = dr.getStockValueAt(Instant.now(), ingredientId);
        Ingredient ing = dr.findIngredientById(ingredientId);
        StockValue sv0o = ing.getStockValueAt(Instant.now());
        
        System.out.println("DB: " + svDb.getQuantity());
        System.out.println("00: " + sv0o.getQuantity());
    }
}