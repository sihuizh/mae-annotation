/*
 * MAE - Multi-purpose Annotation Environment
 *
 * Copyright Keigh Rim (krim@brandeis.edu)
 * Department of Computer Science, Brandeis University
 * Original program by Amber Stubbs (astubbs@cs.brandeis.edu)
 *
 * MAE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, @see <a href="http://www.gnu.org/licenses">http://www.gnu.org/licenses</a>.
 *
 * For feedback, reporting bugs, use the project repo on github
 * @see <a href="https://github.com/keighrim/mae-annotation">https://github.com/keighrim/mae-annotation</a>
 */

package edu.brandeis.cs.nlp.mae.database;


import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import edu.brandeis.cs.nlp.mae.io.MaeIODTDException;
import edu.brandeis.cs.nlp.mae.io.NewDTDLoader;
import edu.brandeis.cs.nlp.mae.io.NewXMLLoader;
import edu.brandeis.cs.nlp.mae.model.*;
import edu.brandeis.cs.nlp.mae.util.HashedSet;
import edu.brandeis.cs.nlp.mae.util.SpanHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

// TODO 151225 split this big chunk of drivers into
// 1) MaeDriverI.java (interface with setupDB, dropDB, etc)
// 2) BaseDriverImpl.java (with sqlite in local dir implementation, maybe we can discuss further on diff impl like hashed sqlite in system temp dir)
// 3) TagDriver.java, AttDriver.java, etc (names are subject to change)

public class DatabaseDriver {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private String DATABASE_URL;
    private ConnectionSource cs;
    private IdHandler idHandler;
    // this should be distinguishable over diff tasks and diff versions
    private String dtdName;
    private String workingFileName;

    // TODO 151227 add another table: task with columns: dtd_root, dtd_filename, last_saved_xml_filename, ...

    private Dao<CharIndex, Integer> charIndexDao;
    private Dao<TagType, Integer> tagTypeDao;
    private Dao<ExtentTag, String> eTagDao;
    private Dao<LinkTag, String> lTagDao;
    private Dao<AttributeType, Integer> attTypeDao;
    private Dao<Attribute, Integer> attDao;
    private Dao<ArgumentType, Integer> argTypeDao;
    private Dao<Argument, Integer> argDao;

    private QueryBuilder<CharIndex, Integer> charIndexQuery;
    private QueryBuilder<TagType, Integer> tagTypeQuery;
    private QueryBuilder<ExtentTag, String> eTagQuery;
    private QueryBuilder<LinkTag, String> lTagQuery;
    private QueryBuilder<AttributeType, Integer> attTypeQuery;
    private QueryBuilder<Attribute, Integer> attQuery;
    private QueryBuilder<ArgumentType, Integer> argTypeQuery;
    private QueryBuilder<Argument, Integer> argQuery;

    private Dao[] allDaos;
    private QueryBuilder[] allQueryBuilders;

    public DatabaseDriver(String databaseUrl) throws SQLException {
        DATABASE_URL = databaseUrl;
        cs = new JdbcConnectionSource(DATABASE_URL);
        idHandler = new IdHandler();
        this.setupDatabase(cs);

    }

    private void setupDatabase(ConnectionSource source) throws SQLException {

        charIndexDao = DaoManager.createDao(source, CharIndex.class);
        tagTypeDao = DaoManager.createDao(source, TagType.class);
        eTagDao = DaoManager.createDao(source, ExtentTag.class);
        lTagDao = DaoManager.createDao(source, LinkTag.class);
        attTypeDao = DaoManager.createDao(source, AttributeType.class);
        attDao = DaoManager.createDao(source, Attribute.class);
        argTypeDao = DaoManager.createDao(source, ArgumentType.class);
        argDao = DaoManager.createDao(source, Argument.class);

        charIndexQuery = charIndexDao.queryBuilder();
        tagTypeQuery = tagTypeDao.queryBuilder();
        eTagQuery = eTagDao.queryBuilder();
        lTagQuery = lTagDao.queryBuilder();
        attTypeQuery = attTypeDao.queryBuilder();
        attQuery = attDao.queryBuilder();
        argTypeQuery = argTypeDao.queryBuilder();
        argQuery = argDao.queryBuilder();

        allDaos = new Dao[]{ charIndexDao, tagTypeDao, eTagDao, lTagDao, attTypeDao, attDao, argTypeDao, argDao};

        allQueryBuilders = new QueryBuilder[]{ charIndexQuery, tagTypeQuery, eTagQuery, lTagQuery, attTypeQuery, attQuery, argTypeQuery, argQuery};

        dropAllTables(source);
        createAllTables(source);

    }

    private void createAllTables(ConnectionSource source) throws SQLException {
        for (Dao dao : allDaos) {
            TableUtils.createTable(source, dao.getDataClass());

        }
    }

    protected void dropAllTables(ConnectionSource source) throws SQLException {
        for (Dao dao : allDaos) {
            TableUtils.dropTable(source, dao.getDataClass(), true);

        }
    }

    public void setUpDtd(File file) throws MaeIODTDException, FileNotFoundException, SQLException {
        NewDTDLoader dtdl = new NewDTDLoader(this);
        dtdl.read(file);

    }

    public void readAnnotation(File file) {
        // TODO 151227 implement XMLLoader class
        NewXMLLoader xmll = new NewXMLLoader(this);
        xmll.read(file);

    }

    private void resetQueryBuilders() {
        for (QueryBuilder qb : allQueryBuilders) {
            qb.reset();
        }
    }

    public String getWorkingFileName() {
        return workingFileName;
    }

    public void setWorkingFileName(String fileName) {
        this.workingFileName = fileName;
    }

    public String getDtdName() {
        return dtdName;
    }

    public void setDtdName(String name) {
        this.dtdName = name;
    }

    List<ExtentTag> getTagsAt(int loc) throws SQLException {

        List<ExtentTag> results = null;
        charIndexQuery.where().eq(DBSchema.TAB_CI_COL_LOCATION, loc);
        results = eTagQuery.join(charIndexQuery).query();
        resetQueryBuilders();
        return results;
    }

    List<String> getTagIdsAt(int loc) throws SQLException {
        List<String> tids = new ArrayList<>();
        for (ExtentTag tag : getTagsAt(loc)) {
            tids.add(tag.getId());
        }
        return tids;
    }

    public HashedSet<CharIndex, ExtentTag> getAllLocationsWithTags() throws SQLException{
        // TODO 151214 when making hghlights, implement getProperColor()
        // to get the first turned-on TagType from a sorted List<TagType>, and also check that's the last (to make it bold)

        HashedSet<CharIndex, ExtentTag> locationsWithTags = new HashedSet<>();

        for (CharIndex location : charIndexDao.queryForAll()) {
            locationsWithTags.putItem(location, location.getTag());
        }

        return locationsWithTags;

    }

    public List<Integer> getAllLocationsOfTagType(TagType type) throws SQLException{

        List<CharIndex> locations;

        if (type.isExtent()) {
            eTagQuery.where().eq(DBSchema.TAB_TAG_FCOL_TT, type);
            locations = charIndexQuery.join(eTagQuery).query();

        } else {
            lTagQuery.where().eq(DBSchema.TAB_TAG_FCOL_TT, type);
            argQuery.join(lTagQuery).selectColumns(DBSchema.TAB_ARG_FCOL_ETAG).distinct();
            eTagQuery.join(argQuery);
            locations = charIndexQuery.join(eTagQuery).query();

        }

        ArrayList<Integer> locationList = new ArrayList<>();
        for (CharIndex ci : locations) {
            locationList.add(ci.getLocation());
        }
        resetQueryBuilders();
        return locationList;

    }

    public List<Integer> getAllLocationsOfTagType(TagType type, List<TagType> exculdes) throws SQLException{
        List<Integer> targetSpans = getAllLocationsOfTagType(type);
        for (TagType exclude : exculdes) {
            targetSpans.removeAll(getAllLocationsOfTagType(exclude));
        }
        return targetSpans;

    }

    public List<ExtentTag> getArgumentTags(LinkTag linker) {
        return linker.getArgumentTags();
    }

    public int[] getSpansByTid(String tid) throws SQLException{
        return eTagDao.queryForId(tid).getSpansAsArray();
    }

    public TagType getTagTypeByTid(String tid) throws SQLException {
        if (eTagDao.queryForId(tid) != null) {
            return eTagDao.queryForId(tid).getTagtype();
        } else {
            return lTagDao.queryForId(tid).getTagtype();
        }
    }

    public void removeTag(Tag tag) throws Exception {
        if (tag instanceof ExtentTag) {
            eTagDao.delete((ExtentTag) tag);
        } else {
            lTagDao.delete((LinkTag) tag);
        }
    }

    HashedSet<TagType, LinkTag> getLinksHasArgumentOf(ExtentTag argument) throws SQLException{
        HashedSet<TagType, LinkTag> links = new HashedSet<>();
        List<Argument> results = argQuery.where().eq(DBSchema.TAB_ARG_FCOL_ETAG, argument).query();
        for (Argument result : results) {
            LinkTag linker = result.getLinker();
            links.putItem(linker.getTagtype(), linker);
        }
        resetQueryBuilders();
        return links;
    }

    public HashedSet<TagType, ExtentTag> getAllExtentTagsByTypes(boolean consumingOnly) throws SQLException {
        HashedSet<TagType, ExtentTag> tagsByTypes = new HashedSet<>();
        for (TagType type : tagTypeDao.queryForAll()) {
            if (type.isExtent()) {
                tagsByTypes.putCollection(type, type.getExtentTagsAsList(consumingOnly));
            }
        }
        return tagsByTypes;

    }

    HashedSet<TagType,ExtentTag> getTagsByTypesAt(int location) throws SQLException{
        HashedSet<TagType, ExtentTag> tags = new HashedSet<>();
        for (ExtentTag tag : getTagsAt(location)) {
            tags.putItem(tag.getTagtype(), tag);
        }
        return tags;
    }

    HashedSet<TagType,ExtentTag> getTagsByTypesIn(int...locations) throws SQLException{
        HashedSet<TagType, ExtentTag> tags = new HashedSet<>();
        for (int location : locations) {
            tags.merge(getTagsByTypesAt(location));
        }
        return tags;
    }

    HashedSet<TagType,ExtentTag> getTagsByTypesIn(String spansString) throws SQLException {
        return getTagsByTypesIn(SpanHandler.convertStringToPairs(spansString));
    }

    HashedSet<TagType,ExtentTag> getTagsByTypesIn(ArrayList<int[]> spansPairs) throws SQLException {
        HashedSet<TagType, ExtentTag> tags = new HashedSet<>();
        for (int[] pair : spansPairs) {
            tags.merge(getTagsByTypesBetween(pair[0], pair[1]));
        }
        return tags;
    }

    HashedSet<TagType,ExtentTag> getTagsByTypesBetween(int begin, int end) throws SQLException{
        HashedSet<TagType, ExtentTag> tags = new HashedSet<>();
        for (int i=begin; i<end; i++) {
            tags.merge(getTagsByTypesAt(i));
        }
        return tags;
    }

    List<? extends Tag> getAllTagsOfType(TagType type) throws SQLException {
        // TODO 151215 split into two methods if necessary (each for link and etag)
        tagTypeDao.refresh(type);
        return new ArrayList<>(type.getTags());
    }

    @SuppressWarnings("unchecked")
    List<ExtentTag> getAllExtentTagsOfType(TagType type) throws SQLException, IllegalArgumentException {
        return (List<ExtentTag>) getAllTagsOfType(type);
    }

    @SuppressWarnings("unchecked")
    List<LinkTag> getAllLinkTagsOfType(TagType type) throws SQLException, IllegalArgumentException {
        return (List<LinkTag>) getAllTagsOfType(type);

    }

    public TagType createTagType(String typeName, String prefix, boolean isLink) throws SQLException {
        TagType type  = new TagType(typeName, prefix, isLink);
        tagTypeDao.create(type);
        return type;
    }

    public AttributeType createAttributeType(TagType tagType, String attName) throws  SQLException {
        AttributeType attType = new AttributeType(tagType, attName);
        attTypeDao.create(attType);
        return attType;
    }

    public ArgumentType createArgumentType(TagType tagType, String argName) throws SQLException {
        ArgumentType argType = new ArgumentType(tagType, argName);
        argTypeDao.create(argType);
        return argType;
    }

    public ExtentTag createExtentTag(String tid, TagType tagType, String text, int...spans) throws SQLException {
        ExtentTag tag = new ExtentTag(tid, tagType, workingFileName);
        tag.setText(text);
        for (CharIndex ci: tag.setSpans(spans)) { charIndexDao.create(ci); }
        eTagDao.create(tag);
        idHandler.addId(tagType, tid);
        return tag;
    }

    public ExtentTag createExtentTag(String tid, TagType tagType, String text, ArrayList<int[]> spans) throws SQLException {
        return createExtentTag(tid, tagType, text, SpanHandler.convertPairsToArray(spans));
    }

    public ExtentTag createExtentTag(String tid, TagType tagType, String text, String spansString) throws SQLException {
        return createExtentTag(tid, tagType, text, SpanHandler.convertStringToPairs(spansString));
    }

    //no tid: how to get tid?
    public ExtentTag createExtentTag(TagType tagType, String text, int...spans) throws SQLException {
//        return createExtentTag(tid, tagType, getTextFromSpans(spans), ModelHelpers.convertArrayToPairs(spans));
        return null;
    }

    public ExtentTag createExtentTag(TagType tagType, String text, ArrayList<int[]> spans) throws SQLException {
//        return createExtentTag(tid, tagType, getTextFromSpans(spans), spans);
        return null;
    }

    public ExtentTag createExtentTag(TagType tagType, String text, String spansString) throws SQLException {
//        return createExtentTag(tid, tagType, getTextFromSpans(spans), ModelHelpers.convertStringToPairs(spansString));
        return null;
    }

    public LinkTag createLinkTag(String tid, TagType tagType) throws SQLException {
        LinkTag link = new LinkTag(tid, tagType, workingFileName);
        lTagDao.create(link);
        return link;
    }

    public LinkTag createLinkTag(String tid, TagType tagType, HashMap<ArgumentType, ExtentTag> arguments) throws SQLException {
        LinkTag link = new LinkTag(tid, tagType, workingFileName);
        for (ArgumentType argType : arguments.keySet()) {
            addArgument(link, argType, arguments.get(argType));
        }
        lTagDao.create(link);
        return link;
    }

    public void addAttribute(Tag tag, AttributeType attType, String attValue) throws SQLException, MaeModelException {
        attDao.create(new Attribute(tag, attType, attValue));

    }

    public void addArgument(LinkTag linker, ArgumentType argType, ExtentTag argument) throws SQLException {
        argDao.create(new Argument(linker, argType, argument));

    }

    /**
     * Shut down data source connection and delete all table from DB.
     */
    public void destroy() throws SQLException {
        if (cs != null){
            dropAllTables(cs);
            cs.close();
        }
    }

    public List<TagType> getTagTypes(boolean includeExtent, boolean includeLink) throws SQLException {
        ArrayList<TagType> types = new ArrayList<>();
        for (TagType type : tagTypeDao.queryForAll()) {
            if (type.isLink() && includeLink) {
                types.add(type);
            } else if (type.isExtent() && includeExtent) {
                types.add(type);
            }
        }
        return types;
    }

    public List<TagType> getAllTagTypes() throws SQLException {
        return getTagTypes(true, true);
    }

    public List<TagType> getExtentTagTypes() throws SQLException {
        return getTagTypes(true, false);
    }

    public List<TagType> getLinkTagTypes() throws SQLException {
        return getTagTypes(false, true);
    }

    public List<TagType> getNonConsumingTagTypes() throws SQLException {
        ArrayList<TagType> types = new ArrayList<>();
        for (TagType type : tagTypeDao.queryForAll()) {
            if (type.isNonConsuming()) {
                types.add(type);
            }
        }
        return types;
    }

    public boolean idExists(String tid) throws SQLException {
        return (eTagDao.queryForId(tid) != null || lTagDao.queryForId(tid) != null);

    }

    public TagType getTagTypeByName(String typeName) throws SQLException {
        return tagTypeDao.queryForEq(DBSchema.TAB_TT_COL_NAME, typeName).get(0);
    }

    // TODO 151216 do we need this?
//    public boolean hasDTD() {
//        return hasDTD;
//    }

    // TODO 151216 how to keep DTD name in the future?
//    public String getDTDName() {
//        return mDtd.getName();
//    }

    public List<ArgumentType> getArgumentTypesOfLinkTagType(TagType link) throws SQLException {
        return new ArrayList<>(argTypeDao.queryForEq(DBSchema.TAB_ART_FCOL_TT, link));
   }

    public boolean setTagTypePrefix(TagType tagType, String prefix) throws SQLException {
        tagType.setPrefix(prefix);
        return tagTypeDao.update(tagType) == 1;
    }

    public boolean setTagTypeNonConsuming(TagType tagType, boolean b) throws SQLException {
        tagType.setNonConsuming(b);
        return tagTypeDao.update(tagType) == 1;
    }

    public void setAttributeValueSet(AttributeType attType, List<String> validValues) throws SQLException {
        attType.setValuesetFromList(validValues);
        attTypeDao.update(attType);
    }

    public void setAttributeValueSet(AttributeType attType, String...validValues) throws SQLException {
        setAttributeValueSet(attType, Arrays.asList(validValues));
    }

    public void setAttributeDefaultValue(AttributeType attType, String defaultValue) throws SQLException {
        attType.setDefaultValue(defaultValue);
        attTypeDao.update(attType);
    }

    public void setAttributeIDRef(AttributeType attType, boolean b) throws SQLException {
        attType.setIdRef(b);
        attTypeDao.update(attType);
    }

    public void setArgumentRequired(ArgumentType argType) throws SQLException {
        argType.setRequired(true);
        argTypeDao.update(argType);
    }

    public void setAttributeRequired(AttributeType attType) throws SQLException {
        attType.setRequired(true);
        attTypeDao.update(attType);

    }
}

