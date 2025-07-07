package com.demo.rest;

import jakarta.annotation.Resource;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Path("/api")
public class ProductResource {

    /** Db2 用 JNDI 名 */
    @Resource(lookup = "jdbc/Db2DS")
    private DataSource ds;

    /* ------------------------------
       /products  : 全商品取得
       ------------------------------ */
    @GET
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> getProducts() throws SQLException {
        List<Product> products = new ArrayList<>();

        String sql =
            "SELECT p.id, p.name, p.description, p.category_id, p.brand_id, " +
            "       c.name AS category_name, b.name AS brand_name " +
            "FROM   products p " +
            "LEFT  JOIN categories c ON p.category_id = c.id " +
            "LEFT  JOIN brands     b ON p.brand_id    = b.id";

        try (Connection conn = ds.getConnection();
             Statement st   = conn.createStatement();
             ResultSet rs   = st.executeQuery(sql)) {

            while (rs.next()) {
                Product p = new Product();
                p.setId(rs.getLong("id"));
                p.setName(rs.getString("name"));
                p.setDescription(rs.getString("description"));
                p.setCategoryId(rs.getLong("category_id"));
                p.setBrandId(rs.getLong("brand_id"));
                p.setCategoryName(rs.getString("category_name"));
                p.setBrandName(rs.getString("brand_name"));
                products.add(p);
            }
        }
        return products;
    }

    /* ------------------------------
       /search  : 商品検索（ページング）
       ------------------------------ */
    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> searchProducts(
            @QueryParam("productName")  String productName,
            @QueryParam("categoryName") String categoryName,
            @QueryParam("brandName")    String brandName,
            @QueryParam("page") @DefaultValue("1") int page) throws SQLException {

        List<Product> products = new ArrayList<>();
        final int pageSize = 100;
        final int offset   = (page - 1) * pageSize;

        final String SEARCH_SQL =
                "SELECT p.id, p.name, p.description, " +
                "       c.name AS category_name, " +
                "       b.name AS brand_name " +
                "FROM   products p " +
                "LEFT JOIN categories c ON c.id = p.category_id " +
                "LEFT JOIN brands     b ON b.id = p.brand_id " +
                "WHERE  (? IS NULL OR ? = '' OR UPPER(p.name) LIKE '%' || UPPER(?) || '%' " +
                "                       OR UPPER(p.description) LIKE '%' || UPPER(?) || '%') " +
                "  AND  (? IS NULL OR ? = '' OR UPPER(c.name) LIKE '%' || UPPER(?) || '%') " +
                "  AND  (? IS NULL OR ? = '' OR UPPER(b.name) LIKE '%' || UPPER(?) || '%') " +
                "ORDER BY p.id " +
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

            try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(SEARCH_SQL)) {

                for (int i = 1; i <= 4;  i++) ps.setString(i,  productName);
                for (int i = 5; i <= 7;  i++) ps.setString(i,  categoryName);
                for (int i = 8; i <= 10; i++) ps.setString(i,  brandName);
                ps.setInt(11, offset);
                ps.setInt(12, pageSize);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product p = new Product();
                    p.setId(rs.getLong("id"));
                    p.setName(rs.getString("name"));
                    p.setDescription(rs.getString("description"));
                    p.setCategoryName(rs.getString("category_name"));
                    p.setBrandName(rs.getString("brand_name"));
                    products.add(p);
                }
            }
        }
        return products;
    }

    /* ------------------------------
       /search/count : ヒット件数
       ------------------------------ */
    @GET
    @Path("/search/count")
    @Produces(MediaType.APPLICATION_JSON)
    public int searchProductsCount(
            @QueryParam("productName")  String productName,
            @QueryParam("categoryName") String categoryName,
            @QueryParam("brandName")    String brandName) throws SQLException {

        final String COUNT_SQL =
                "SELECT COUNT(*) AS total " +
                "FROM   products p " +
                "LEFT JOIN categories c ON c.id = p.category_id " +
                "LEFT JOIN brands     b ON b.id = p.brand_id " +
                "WHERE  (? IS NULL OR ? = '' OR UPPER(p.name) LIKE '%' || UPPER(?) || '%' " +
                "                       OR UPPER(p.description) LIKE '%' || UPPER(?) || '%') " +
                "  AND  (? IS NULL OR ? = '' OR UPPER(c.name) LIKE '%' || UPPER(?) || '%') " +
                "  AND  (? IS NULL OR ? = '' OR UPPER(b.name) LIKE '%' || UPPER(?) || '%')";

        try (Connection conn = ds.getConnection();
            PreparedStatement ps = conn.prepareStatement(COUNT_SQL)) {

            for (int i = 1; i <= 4;  i++) ps.setString(i, productName);
            for (int i = 5; i <= 7;  i++) ps.setString(i, categoryName);
            for (int i = 8; i <= 10; i++) ps.setString(i, brandName);

                    try (ResultSet rs = ps.executeQuery()) {
                        return (rs.next()) ? rs.getInt("total") : 0;
                    }
                }
            }

    /* ------------------------------
       /categories : カテゴリ一覧
       ------------------------------ */
    @GET
    @Path("/categories")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Category> getCategories() throws SQLException {
        List<Category> categories = new ArrayList<>();

        try (Connection conn = ds.getConnection();
             Statement st   = conn.createStatement();
             ResultSet rs   = st.executeQuery("SELECT id, name FROM categories")) {

            while (rs.next()) {
                Category c = new Category();
                c.setId(rs.getLong("id"));
                c.setName(rs.getString("name"));
                categories.add(c);
            }
        }
        return categories;
    }

    /* ------------------------------
       /brands : ブランド一覧
       ------------------------------ */
    @GET
    @Path("/brands")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Brand> getBrands() throws SQLException {
        List<Brand> brands = new ArrayList<>();

        try (Connection conn = ds.getConnection();
             Statement st   = conn.createStatement();
             ResultSet rs   = st.executeQuery("SELECT id, name FROM brands")) {

            while (rs.next()) {
                Brand b = new Brand();
                b.setId(rs.getLong("id"));
                b.setName(rs.getString("name"));
                brands.add(b);
            }
        }
        return brands;
    }
}