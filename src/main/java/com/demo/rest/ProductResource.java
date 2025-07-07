package com.demo.rest;

import jakarta.annotation.Resource;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Product REST API (Liberty + Db2 11.5)
 *
 * - Full‑text search (Db2 Text Search)
 * - Key‑set (seek) paging
 *
 *  - afterId   : 前ページの最終 ID（初回は 0）
 *  - pageSize  : 1 ページあたり件数（既定 40）
 *
 *  検索結果は ID 昇順で返す。フロント側は
 *    nextAfterId = list.isEmpty() ? null : list.get(list.size() - 1).getId();
 *  を次ページの afterId に設定する。
 */
@Path("/api")
public class ProductResource {

    /** Db2 用 JNDI 名 */
    @Resource(lookup = "jdbc/Db2DS")
    private DataSource ds;

    /* --------------------------------------------------
       /search  : 商品検索（全文検索 + key‑set）
       -------------------------------------------------- */
    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> searchProducts(
            @QueryParam("keyword")      String keyword,
            @QueryParam("categoryName") String categoryName,
            @QueryParam("brandName")    String brandName,
            @QueryParam("afterId")      @DefaultValue("0") long afterId,
            @QueryParam("pageSize")     @DefaultValue("40") int pageSize) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.name, p.description, ")
           .append("       c.name AS category_name, ")
           .append("       b.name AS brand_name ")
           .append("FROM   products p ")
           .append("JOIN   categories c ON c.id = p.category_id ")
           .append("JOIN   brands     b ON b.id = p.brand_id ")
           .append("WHERE  p.id > ?");     // key‑set cursor

        List<Object> params = new ArrayList<>();
        params.add(afterId);

        // --- full‑text search ------------------------------------------------
        if (!isNullOrEmpty(keyword)) {
            sql.append(" AND (CONTAINS(p.name, ?) = 1 OR CONTAINS(p.description, ?) = 1)");
            params.add(keyword);
            params.add(keyword);
        }

        // --- その他のフィルタ ------------------------------------------------
        if (!isNullOrEmpty(categoryName)) {
            sql.append(" AND UPPER(c.name) LIKE ?");
            params.add(toLikePattern(categoryName));
        }
        if (!isNullOrEmpty(brandName)) {
            sql.append(" AND UPPER(b.name) LIKE ?");
            params.add(toLikePattern(brandName));
        }

        sql.append(" ORDER BY p.id FETCH FIRST ? ROWS ONLY");
        params.add(pageSize);

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepare(conn, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            return mapProducts(rs);
        }
    }

    /* --------------------------------------------------
       /search/count : ヒット件数
       ※ offset 廃止後も件数が欲しい場合のみ使用
       -------------------------------------------------- */
    @GET
    @Path("/search/count")
    @Produces(MediaType.APPLICATION_JSON)
    public int searchProductsCount(
            @QueryParam("keyword")      String keyword,
            @QueryParam("categoryName") String categoryName,
            @QueryParam("brandName")    String brandName) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) AS total ")
           .append("FROM   products p ")
           .append("JOIN   categories c ON c.id = p.category_id ")
           .append("JOIN   brands     b ON b.id = p.brand_id ")
           .append("WHERE 1=1");

        List<Object> params = new ArrayList<>();

        if (!isNullOrEmpty(keyword)) {
            sql.append(" AND (CONTAINS(p.name, ?) = 1 OR CONTAINS(p.description, ?) = 1)");
            params.add(keyword);
            params.add(keyword);
        }
        if (!isNullOrEmpty(categoryName)) {
            sql.append(" AND UPPER(c.name) LIKE ?");
            params.add(toLikePattern(categoryName));
        }
        if (!isNullOrEmpty(brandName)) {
            sql.append(" AND UPPER(b.name) LIKE ?");
            params.add(toLikePattern(brandName));
        }

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepare(conn, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    /* --------------------------------------------------
       検索条件無しの一覧取得 (変更なし)
       -------------------------------------------------- */
    @GET
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> getProducts() throws SQLException {
        String sql =
            "SELECT p.id, p.name, p.description, "+
            "       c.name AS category_name, b.name AS brand_name "+
            "FROM   products p "+
            "JOIN   categories c ON c.id = p.category_id "+
            "JOIN   brands     b ON b.id = p.brand_id";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapProducts(rs);
        }
    }

    /* --------------------------------------------------
       /categories, /brands も変更なし
       -------------------------------------------------- */
    @GET
    @Path("/categories")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Category> getCategories() throws SQLException {
        String sql = "SELECT id, name FROM categories ORDER BY id";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Category> list = new ArrayList<>();
            while (rs.next()) {
                Category c = new Category();
                c.setId(rs.getLong("id"));
                c.setName(rs.getString("name"));
                list.add(c);
            }
            return list;
        }
    }

    @GET
    @Path("/brands")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Brand> getBrands() throws SQLException {
        String sql = "SELECT id, name FROM brands ORDER BY id";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Brand> list = new ArrayList<>();
            while (rs.next()) {
                Brand b = new Brand();
                b.setId(rs.getLong("id"));
                b.setName(rs.getString("name"));
                list.add(b);
            }
            return list;
        }
    }

    /* --------------------------------------------------
       共通ユーティリティ
       -------------------------------------------------- */
    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String toLikePattern(String keyword) {
        return "%" + keyword.toUpperCase() + "%";
    }

    /** PreparedStatement にリストの値を順番にバインド */
    private static PreparedStatement prepare(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            Object val = params.get(i);
            if (val instanceof String) {
                ps.setString(i + 1, (String) val);
            } else if (val instanceof Integer) {
                ps.setInt(i + 1, (int) val);
            } else if (val instanceof Long) {
                ps.setLong(i + 1, (long) val);
            } else {
                ps.setObject(i + 1, val);
            }
        }
        return ps;
    }

    /** ResultSet → List<Product> */
    private static List<Product> mapProducts(ResultSet rs) throws SQLException {
        List<Product> list = new ArrayList<>();
        while (rs.next()) {
            Product p = new Product();
            p.setId(rs.getLong("id"));
            p.setName(rs.getString("name"));
            p.setDescription(rs.getString("description"));
            p.setCategoryName(rs.getString("category_name"));
            p.setBrandName(rs.getString("brand_name"));
            list.add(p);
        }
        return list;
    }
}
