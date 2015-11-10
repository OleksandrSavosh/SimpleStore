package com.github.aleksandrsavosh.simplestore.parse;

import com.github.aleksandrsavosh.simplestore.*;
import com.github.aleksandrsavosh.simplestore.exception.CreateException;
import com.github.aleksandrsavosh.simplestore.exception.DeleteException;
import com.github.aleksandrsavosh.simplestore.exception.ReadException;
import com.github.aleksandrsavosh.simplestore.exception.UpdateException;
import com.parse.ParseException;
import com.parse.ParseObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ParseSimpleStoreImpl<Model extends Base> implements SimpleStore<Model, String> {

    private Class<Model> clazz;

    public ParseSimpleStoreImpl(Class clazz) {
        this.clazz = clazz;
    }

    @Override
    public Model create(Model model) {
        try {
            return createThrowException(model);
        } catch (CreateException e) {
            LogUtil.toLog("Create model error", e);
        }
        return null;
    }

    @Override
    public Model read(String s) {
        try {
            return readThrowException(s);
        } catch (ReadException e) {
            LogUtil.toLog("Read model error", e);
        }
        return null;
    }

    @Override
    public Model update(Model model) {
        try {
            return updateThrowException(model);
        } catch (UpdateException e) {
            LogUtil.toLog("Update model error", e);
        }
        return null;
    }

    @Override
    public boolean delete(String s) {
        try {
            return deleteThrowException(s);
        } catch (DeleteException e) {
            LogUtil.toLog("Delete model error", e);
        }
        return false;
    }

    @Override
    public Model createThrowException(Model model) throws CreateException {
        try {
            return createThrowExceptionCommon(model);
        } catch (Exception e) {
            throw new CreateException(e.getMessage(), e);
        }
    }

    public <T extends Base> T createThrowExceptionCommon(T model) throws ParseException, IllegalAccessException {
        Class clazz = model.getClass();
        ParseObject po = ParseUtil.createPO(clazz);
        ParseUtil.setModel2PO(model, po);
        ParseUtil.setModelData2PO(model, po);
        po.save();
        ParseUtil.setPO2Model(po, model);
        return model;
    }

    @Override
    public Model readThrowException(String s) throws ReadException {
        try {
            return readThrowExceptionCommon(s, clazz);
        } catch (Exception e){
            throw new ReadException(e);
        }
    }

    public <T extends Base> T readThrowExceptionCommon(String id, Class<T> forClass) throws ParseException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        ParseObject po = ParseUtil.getPO(forClass, id);
        T model = ParseUtil.createModel(forClass);
        ParseUtil.setPO2Model(po, model);
        ParseUtil.setPOData2Model(po, model);
        return model;

    }

    @Override
    public Model updateThrowException(Model model) throws UpdateException {
        try {
            ParseObject po = ParseUtil.getPO(clazz, model.getCloudId());
            ParseUtil.setModel2PO(model, po);
            po.save();
            ParseUtil.setPO2Model(po, model);
            return model;
        } catch(Exception e){
            throw new UpdateException(e);
        }
    }

    @Override
    public boolean deleteThrowException(String s) throws DeleteException {
        try {
            ParseObject po = ParseUtil.getPO(clazz, s);
            po.delete();
            return true;
        } catch (Exception e){
            throw new DeleteException(e);
        }
    }

    @Override
    public Model createWithRelations(Model model) {
        try {
            return createWithRelationsThrowException(model);
        } catch (CreateException e){
            LogUtil.toLog("Create with relations error", e);
        }
        return null;
    }

    @Override
    public Model readWithRelations(String s) {
        return null;
    }

    @Override
    public boolean deleteWithRelations(String s) {
        return false;
    }

    @Override
    public Model createWithRelationsThrowException(Model model) throws CreateException {
        try {
            return createWithRelationsThrowExceptionCommon(model);
        } catch(Exception e){
            throw new CreateException(e);
        }
    }

    private <T extends Base> T createWithRelationsThrowExceptionCommon(T model) throws CreateException {
        try {
            //create parent
            model = createThrowExceptionCommon(model);

            //create children
            List<? extends Base> children = SimpleStoreUtil.getModelChildrenObjects(model);
            for(Base child : children){
                child = createWithRelationsThrowExceptionCommon(child);

                //create relations
                if(!appendChildToParentCommon(model, child)){
                    throw new CreateException("Not create relation");
                }
            }

            return model;
        } catch (Exception e) {
            throw new CreateException("Can not create model with relations", e);
        }
    }

    private <Parent extends Base, Child extends Base> boolean appendChildToParentCommon(Parent parent, Child child)
            throws ParseException {
        ParseObject parentPo = ParseUtil.getPO(parent.getClass(), parent.getCloudId());
        ParseObject childPo = ParseUtil.getPO(child.getClass(), child.getCloudId());
        childPo.put(parentPo.getClassName(), parentPo);
        childPo.save();
        return true;
    }

    @Override
    public Model readWithRelationsThrowException(String s) throws ReadException {
        return null;
    }

    public <T extends Base> T readWithRelationsThrowExceptionCommon(String pk, Class<T> forClazz)
            throws ReadException, NoSuchMethodException, InstantiationException, IllegalAccessException, ParseException, InvocationTargetException {
        //read parent
        T model = readThrowExceptionCommon(pk, forClazz);

        //read one to one relations
        for(Field field : ReflectionUtil.getFields(forClazz, new HashSet<Class>(){{ addAll(Const.modelClasses); }})){
            field.setAccessible(true);
            Class type = field.getType();

            List<String> ids = getRelationsIds(pk, forClazz, type);
            if(ids.size() > 1){
                throw new ReadException("To much children for one model property");
            }

            if(ids.size() == 1){
                try {
                    Base child = readWithRelationsThrowExceptionCommon(ids.get(0), type);
                    field.set(model, child);
                } catch (IllegalAccessException e) {
                    throw new ReadException(e);
                }
            }
        }

        //read one to many relations
        for(Field field : ReflectionUtil.getFields(forClazz, Const.collections)) {
            field.setAccessible(true);
            Class collType = field.getType();
            Class genType = ReflectionUtil.getGenericType(field);

            Collection collection = ReflectionUtil.getCollectionInstance(collType);

            List<Long> ids = getRelationsIds(pk, forClazz, genType);
            for(Long id : ids){
                Base child = readWithRelationsThrowExceptionCommon(id, genType);
                collection.add(child);
            }
            try {
                field.set(model, collection);
            } catch (IllegalAccessException e) {
                throw new ReadException(e);
            }
        }

        return model;
    }

    public List<String> getRelationsIds(String pk, Class forClazz, Class type) throws ParseException {
        ParseObject po = ParseUtil.getPO(forClazz, pk);
        po.
    }

    @Override
    public boolean deleteWithRelationsThrowException(String s) throws DeleteException {
        return false;
    }

    @Override
    public List<Model> readAll() {
        return null;
    }

    @Override
    public List<Model> readAllThrowException() throws ReadException {
        return null;
    }

    @Override
    public List<Model> readAllWithRelations() {
        return null;
    }

    @Override
    public List<Model> readAllWithRelationsThrowException() throws ReadException {
        return null;
    }

    @Override
    public List<Model> readBy(KeyValue... keyValues) {
        return null;
    }

    @Override
    public List<Model> readByThrowException(KeyValue... keyValues) throws ReadException {
        return null;
    }

    @Override
    public List<Model> readByWithRelations(KeyValue... keyValues) {
        return null;
    }

    @Override
    public List<Model> readByWithRelationsThrowException(KeyValue... keyValues) throws ReadException {
        return null;
    }

    @Override
    public List<String> readParentIds(Class parentClazz, String id) {
        return null;
    }

    @Override
    public List<String> readParentIdsThrowException(Class parentClazz, String id) {
        return null;
    }

    @Override
    public List<String> readChildrenIds(Class childClazz, String id) {
        return null;
    }

    @Override
    public List<String> readChildrenIdsThrowException(Class childClazz, String id) {
        return null;
    }
}