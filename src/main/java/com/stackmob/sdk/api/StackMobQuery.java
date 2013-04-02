/**
 * Copyright 2011 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stackmob.sdk.api;

import com.stackmob.sdk.util.ListHelpers;
import com.stackmob.sdk.util.Pair;

import java.util.*;

/**
 * A class that builds queries for data access methods like those in {@link StackMob}. Example usage:
 * <pre>
 * {@code
 *     StackMobQuery query = new StackMobQuery("user")
 *                             .fieldIsGreaterThan("age", 20)
 *                             .fieldIsLessThanOrEqualTo("age", 40)
 *                             .fieldIsIn("friend", Arrays.asList("joe", "bob", "alice");
 *
 *     //Of if you have multiple constraints on the same field and you want to type less
 *     StackMobQuery query = new StackMobQuery("user")
 *                             .field(new StackMobQueryField("age").isGreaterThan(20).isLessThanOrEqualTo(40))
 *                             .field(new StackMobQueryField("friend").isIn(Arrays.asList("joe", "bob", "alice")));
 * }
 * </pre>
 *
 * A few helpful notes about this object:
 * <ul>
 *     <li>this class is not thread safe. make sure to synchronize all calls</li>
 *     <li>the {@link #field(StackMobQueryField)} method helps you build up part of your query on a specific field</li>
 *     <li>you can only operate on one field at a time, but you can call field("field") as many times as you want</li>
 *     <li>
 *         you can call methods like fieldIsGreaterThan("field", "value") or fieldIsLessThanOrEqualTo("field", "value") directly on a StackMobQuery object.
 *     </li>
 * </ul>
 */
public class StackMobQuery {

    private String objectName;
    private Map<String, String> headers = new HashMap<String, String>();
    private Map<String, String> args = new HashMap<String, String>();
    private int orCount = 1;
    private int andCount = 1;
    private boolean isAnd = false;
    private boolean isOr = false;

    private static final String OrFormat = "[or%d].";
    private static final String AndFormat = "[and%d].";

    private static final String RangeHeader = "Range";

    private static final String OrderByHeader = "X-StackMob-OrderBy";


    /**
     * Represents ascending or descending order
     */
    public static enum Ordering {
        DESCENDING("desc"),
        ASCENDING("asc");

        private String name;
        Ordering(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    static enum Operator {
        LT("lt"),
        GT("gt"),
        LTE("lte"),
        GTE("gte"),
        IN("in"),
        NEAR("near"),
        WITHIN("within"),
        NE("ne"),
        NULL("null"),
        EMPTY("empty");

        private String operator;

        Operator(String operator) {
            this.operator = operator;
        }

        public String getOperatorForURL() {
            return "["+operator+"]";
        }
    }

    /**
     * creates an empty query. {@link #StackMobQuery(String)} should be used in almost all cases. Only use
     * this if you then later call {@link #setObjectName(String)}
     */
    public StackMobQuery() {}

    /**
     * create a query on a specific object
     * @param objectName the schema you're querying against
     */
    public StackMobQuery(String objectName) {
        this.objectName = objectName;
    }

    /**
     * create a query on a specific object
     * @param objectName the schema you're querying against
     * @return the new query
     */
    public static StackMobQuery objects(String objectName) {
        return new StackMobQuery(objectName);
    }

    /**
     * set the schema being queried against
     * @param objectName the schema
     */
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    /**
     * get the schema being queried against
     * @return objectName the schema
     */
    public String getObjectName() {
        return objectName;
    }

    /**
     * get the headers generated by this query
     * @return headers
     */
    public Map<String, String> getHeaders() {
        return this.headers;
    }

    /**
     * get the arguments generated by this query
     * @return arguments
     */
    public List<Map.Entry<String, String>> getArguments() {
        //If the top level was marked specifically as OR, we need to add an extra layer
        Map<String, String> finalMap = isOr ? prependString(String.format(OrFormat, orCount), this.args) : this.args;
        return new ArrayList<Map.Entry<String, String>>(finalMap.entrySet());
    }

    private Map<String, String> getNestedArguments() {
        return this.args;
    }

    /**
     * copy the constraints in a give query to this one
     * @param other the query to copy
     * @return a new query contain all the constraints of both
     */
    public StackMobQuery add(StackMobQuery other) {
        this.headers.putAll(other.headers);
        this.args.putAll(other.args);
        return this;
    }

    private Map<String,String> prependString(String prefix, Map<String, String> args) {
        Map<String, String> newMap = new HashMap<String, String>();
        for(String arg : args.keySet()) {
            newMap.put(prefix + arg, args.get(arg));
        }
        return newMap;
    }

    /**
     * Separate two constraints with an AND. Constraints are separated
     * like this by default, but this allows your queries to read naturally.
     * You cannot mix AND and OR at the same level of a query; instead you need to
     * start a sub-expression with {@link #and(StackMobQuery clauses)},
     * effectively adding parenthesis
     * @return the query ready to accept another statement
     */
    public StackMobQuery and() {
        if(this.isOr) throw new IllegalStateException("Mixing OR and AND on the same level is not allowed");
        this.isAnd = true;
        return this;
    }

    /**
     * Add a new parenthesized expression to be joined by AND with the
     * other constraints in the query. The sub-expression will be treated
     * as the logical OR of a set of clauses, giving you A && (B || C || ...)
     * StackMob only supports one such parenthesized OR block
     * in a query, but it can contain any number of clauses.
     * @param clauses a sub-query. Each constraint added to it is combined into an OR statement.
     * @return A new query with the OR statement joined with AND to the rest of the clauses.
     */
    public StackMobQuery and(StackMobQuery clauses) {
        if(this.isOr) throw new IllegalStateException("Mixing OR and AND on the same level is not allowed");
        this.isAnd = true;
        String orString = String.format(OrFormat, orCount);
        for(Map.Entry<String, String> arg : prependString(orString, clauses.getNestedArguments()).entrySet()) {
            this.args.put(arg.getKey(), arg.getValue());
        }
        orCount++;
        return this;
    }

    /**
     * Separate two constraints with an OR. This can be done at the top level
     * of a query, or within a sub-query added with {@link #and(StackMobQuery clauses)}
     * You cannot mix AND and OR at the same level of a query; instead you need to
     * start a sub-expression with ,{@link #or(StackMobQuery clauses)}
     * effectively adding parenthesis
     * @return the query ready to accept another statement
     */
    public StackMobQuery or() {
        if(this.isAnd) throw new IllegalStateException("Mixing OR and AND on the same level is not allowed");
        this.isOr = true;
        return this;
    }

    /**
     * Add a new parenthesized expression to be joined by OR with the
     * other constraints in the query. The sub-expression will be treated
     * as the logical AND of a set of clauses, giving you A || (B && C && ...)
     * Redundant nested AND statements will be rejected.
     * @param clauses a sub-query. Each constraint added to it is combined into an AND statement.
     * @return A new query with the AND statement joined with OR to the rest of the clauses
     */
    public StackMobQuery or(StackMobQuery clauses) {
        if(this.isAnd) throw new IllegalStateException("Mixing OR and AND on the same level is not allowed");
        this.isOr = true;
        String andString = String.format(AndFormat, andCount);
        for(Map.Entry<String, String> arg : prependString(andString, clauses.getNestedArguments()).entrySet()) {
            this.args.put(arg.getKey(), arg.getValue());
        }
        andCount++;
        return this;
    }

    /**
     * Begin adding constraints to a field via a {@link StackMobQueryField}
     * @param field the name of the field
     * @return a query specific to that field
     */
    public StackMobQuery field(StackMobQueryField field) {
        add(field.getQuery());
        return this;
    }

    /**
     * add a "NEAR" to your query for the given StackMobGeoPoint field. Query results are automatically returned
     * sorted by distance closest to the queried point
     * @param field the StackMobGeoPoint field whose value to test
     * @param point the lon/lat location to center the search
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsNear(String field, StackMobGeoPoint point) {
        return putInMap(field, Operator.NEAR, ListHelpers.join(point.asList(), ","));
    }

    /**
     * add a "NEAR" to your query for the given StackMobGeoPoint field. Query results are automatically returned
     * sorted by distance closest to the queried point
     * @param field the StackMobGeoPoint field whose value to test
     * @param point the lon/lat location to center the search
     * @param maxDistanceMi the maximum distance in miles a matched field can be from point.
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsNearWithinMi(String field, StackMobGeoPoint point, Double maxDistanceMi) {
        List<String> arguments = point.asList();
        arguments.add(StackMobGeoPoint.miToRadians(maxDistanceMi).toString()); //convert to radians
        return putInMap(field, Operator.NEAR, ListHelpers.join(arguments, ","));
    }

    /**
     * add a "NEAR" to your query for the given StackMobGeoPoint field. Query results are automatically returned
     * sorted by distance closest to the queried point
     * @param field the StackMobGeoPoint field whose value to test
     * @param point the lon/lat location to center the search
     * @param maxDistanceKm the maximum distance in kilometers a matched field can be from point.
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsNearWithinKm(String field, StackMobGeoPoint point, Double maxDistanceKm) {
        List<String> arguments = point.asList();
        arguments.add(StackMobGeoPoint.kmToRadians(maxDistanceKm).toString()); //convert to radians
        return putInMap(field, Operator.NEAR, ListHelpers.join(arguments, ","));
    }

    /**
     * add a "WITHIN" to your query for the given StackMobGeoPoint field. Query results are not sorted by distance.
     * @param field the StackMobGeoPoint field whose value to test
     * @param point the lon/lat location to center the search
     * @param radiusInMi the maximum distance in miles a matched field can be from point.
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsWithinRadiusInMi(String field, StackMobGeoPoint point, Double radiusInMi) {
        List<String> arguments = point.asList();
        arguments.add(StackMobGeoPoint.miToRadians(radiusInMi).toString()); //convert to radians
        return putInMap(field, Operator.WITHIN, ListHelpers.join(arguments, ","));
    }

    /**
     * add a "WITHIN" to your query for the given StackMobGeoPoint field. Query results are not sorted by distance.
     * @param field the StackMobGeoPoint field whose value to test
     * @param point the lon/lat location to center the search
     * @param radiusInKm the maximum distance in kilometers a matched field can be from point.
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsWithinRadiusInKm(String field, StackMobGeoPoint point, Double radiusInKm) {
        List<String> arguments = point.asList();
        arguments.add(StackMobGeoPoint.kmToRadians(radiusInKm).toString()); //convert to radians
        return putInMap(field, Operator.WITHIN, ListHelpers.join(arguments, ","));
    }

    /**
     * add a "WITHIN" to your query for the given StackMobGeoPoint field. Matched fields will be within the 2-dimensional bounds
     * defined by the lowerLeft and upperRight StackMobGeoPoints given
     * @param field the StackMobGeoPoint field whose value to test
     * @param lowerLeft the lon/lat location of the lower left corner of the bounding box
     * @param upperRight the lon/lat location of the upper right corner of the bounding box
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsWithinBox(String field, StackMobGeoPoint lowerLeft, StackMobGeoPoint upperRight) {
        List<String> arguments = lowerLeft.asList();
        arguments.addAll(upperRight.asList());
        return putInMap(field, Operator.WITHIN, ListHelpers.join(arguments, ","));
    }

    /**
     * add an "IN" to your query. test whether the given field's value is in the given list of possible values
     * @param field the field whose value to test
     * @param values the values against which to match
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsIn(String field, List<String> values) {
        return putInMap(field, Operator.IN, ListHelpers.join(values, ","));
    }

    /**
     * add a "NE" to your query. test whether the given field's value is not equal to the given value
     * @param field the field whose value to test
     * @param val the value against which to match
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsNotEqual(String field, String val) {
        if(val == null) {
            return fieldIsNotNull(field);
        }
        if(val == "") {
            return putInMap(field, Operator.EMPTY, "false");
        }
        return putInMap(field, Operator.NE, val);
    }

    /**
     * add a "NULL" to your query. test whether the given field's value is null
     * @param field the field whose value to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsNull(String field) {
        return putInMap(field, Operator.NULL, "true");
    }

    /**
     * add a "NULL" to your query. test whether the given field's value is not null
     * @param field the field whose value to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsNotNull(String field) {
        return putInMap(field, Operator.NULL, "false");
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except works with Strings
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsLessThan(String field, String val) {
        return putInMap(field, Operator.LT, val);
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except works with Strings
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsLessThan(String field, int val) {
        return putInMap(field, Operator.LT, String.valueOf(val));
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except applies "<=" instead of "<"
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIslessThanOrEqualTo(String field, String val) {
        return putInMap(field, Operator.LTE, val);
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except applies "<=" instead of "<"
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsLessThanOrEqualTo(String field, int val) {
        return putInMap(field, Operator.LTE, String.valueOf(val));
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except applies ">" instead of "<"
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsGreaterThan(String field, String val) {
        return putInMap(field, Operator.GT, val);
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except applies ">" instead of "<"
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsGreaterThan(String field, int val) {
        return putInMap(field, Operator.GT, String.valueOf(val));
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except applies ">=" instead of "<"
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsGreaterThanOrEqualTo(String field, String val) {
        return putInMap(field, Operator.GTE, val);
    }

    /**
     * same as {@link #fieldIsLessThan(String, String)}, except applies ">=" instead of "<"
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsGreaterThanOrEqualTo(String field, int val) {
        return putInMap(field, Operator.GTE, String.valueOf(val));
    }

    /**
     * add an "=" to your query. test whether the given field's value is equal to the given value
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsEqualTo(String field, String val) {
        if(val == null) {
            return fieldIsNull(field);
        }
        if(val == "") {
            return putInMap(field, Operator.EMPTY, "true");
        }
        args.put(field, val);
        return this;
    }

    /**
     * add an "=" to your query. test whether the given field's value is equal to the given value
     * @param field the field whose value to test
     * @param val the value against which to test
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsEqualTo(String field, int val) {
        return fieldIsEqualTo(field, String.valueOf(val));
    }

    /**
     * add an "ORDER BY" to your query
     * @param field the field to order by
     * @param ordering the ordering of that field
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery fieldIsOrderedBy(String field, Ordering ordering) {
        String buf = headers.get(OrderByHeader);
        if(buf != null) {
            buf += ",";
        }
        else {
            buf = "";
        }
        buf += field+":"+ordering.toString();
        headers.put(OrderByHeader, buf);
        return this;
    }

    /**
     * this method lets you add a "LIMIT" and "SKIP" to your query at once. Can be used to implement pagination in your app.
     * @param start the starting object number (inclusive)
     * @param end the ending object number (inclusive)
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery isInRange(Integer start, Integer end) {
        headers.put(RangeHeader, "objects="+start.toString()+"-"+end.toString());
        return this;
    }

    /**
     * same thing as {@link #isInRange(Integer, Integer)}, except does not specify an end to the range.
     * instead, gets all objects from a starting point (including)
     * @param start the starting object number
     * @return the new query that resulted from adding this operation
     */
    public StackMobQuery isInRange(Integer start) {
        headers.put(RangeHeader, "objects="+start.toString()+"-");
        return this;
    }

    private StackMobQuery putInMap(String field, Operator operator, String value) {
        args.put(field+operator.getOperatorForURL(), value);
        return this;
    }

    private StackMobQuery putInMap(String field, Operator operator, int value) {
        putInMap(field, operator, Integer.toString(value));
        return this;
    }
}
