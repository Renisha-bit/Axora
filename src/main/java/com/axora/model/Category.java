package com.axora.model;

/**
 * Category entity.
 */
public class Category {

    public enum CategoryType { INCOME, EXPENSE, BOTH }

    private long         id;
    private String       name;
    private CategoryType type;
    private String       color;
    private boolean      isDefault;

    public Category() {}

    public Category(String name, CategoryType type) {
        this.name = name;
        this.type = type;
    }

    public long getId()                     { return id; }
    public void setId(long id)              { this.id = id; }

    public String getName()                 { return name; }
    public void setName(String name)        { this.name = name; }

    public CategoryType getType()           { return type; }
    public void setType(CategoryType type)  { this.type = type; }

    public String getColor()                { return color; }
    public void setColor(String color)      { this.color = color; }

    public boolean isDefault()              { return isDefault; }
    public void setDefault(boolean d)       { this.isDefault = d; }

    @Override
    public String toString()                { return name; }
}
