package com.jpson;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A high-level PSON encoder that maintains a dictionary
 */
public class PsonEncoder extends PsonWriter {


    protected PsonOptions options;

    protected Map<String, Integer> initialDictionary;

    public static byte[] encode(Object structure, List<String> initialDictionary, PsonOptions options) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final PsonEncoder psonEncoder = new PsonEncoder(output, options, initialDictionary);
        psonEncoder.write(structure);
        return output.toByteArray();
    }

    protected PsonEncoder(OutputStream output, PsonOptions options, List<String> initialDictionary) {
        super(output);

        if (options == null) {
            this.options = PsonOptions.None;
        } else {
            this.options = options;
        }
        if (initialDictionary == null)
            this.initialDictionary = null;
        else {
            this.initialDictionary = new Object2IntOpenHashMap<String>(initialDictionary.size());
            int index = 0;
            for (String item : initialDictionary) {
                this.initialDictionary.put(item, index++);
            }
        }
    }

    protected PsonEncoder(OutputStream output, List<String> initialDictionary) {
        new PsonEncoder(output, PsonOptions.None, initialDictionary);
    }

    protected PsonEncoder(OutputStream output) {
        new PsonEncoder(output, null);
    }

    protected void write(Object obj) throws IOException {
        if (obj == null) {
            writeNull();
            return;
        }

        switch (obj) {
            case String s -> writeString(s);
            case Long l -> writeLong(l);
            case Float f -> writeFloat(f);
            case Double d -> writeDouble(d);
            case Boolean b -> writeBool(b);
            case Integer i -> writeInt(i.intValue());
            case Short sh -> writeInt(sh.intValue());
            case Byte by -> writeInt(by.intValue());
            case Object[] arr -> writeArray(arr);
            case List<?> list -> writeList((List<Object>) list);
            case Map<?, ?> map -> writeMap((Map<String, Object>) map);
            default -> writeObject(obj);
        }
    }

    @Override
    protected void writeString(String str) throws IOException {
        writeString(str, false);
    }


    protected void writeStringKey(String str) throws IOException {
        writeString(str, true);
    }


    protected void writeArray(Object[] list) throws IOException {
        if (list == null) {
            writeNull();
            return;
        }
        int count = list.length;
        writeStartArray(count);
        for (Object o : list) write(o);
    }

    protected void writeList(List list) throws IOException {
        if (list == null) {
            writeNull();
            return;
        }
        int count = list.size();
        writeStartArray(count);
        for (int i = 0; i < count; ++i)
            write(list.get(i));
    }

    protected void writeMap(Map<String, Object> map) throws IOException {
        if (map == null) {
            writeNull();
            return;
        }
        writeStartObject(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            write(entry.getKey());
            write(entry.getValue());
        }
    }

    protected void writeObject(Object obj) throws IOException {
        if (obj == null) {
            writeNull();
        }
        Map<String, Object> map = ObjectToMap(obj);
        writeMap(map);
    }

    protected void writeString(String str, boolean isKey) throws IOException {
        if (str == null) {
            writeNull();
            return;
        }
        if (str.length() == 0) {
            writeEmptyString();
            return;
        }
        int index;
        if (initialDictionary != null && initialDictionary.containsKey(str)) {
            index = initialDictionary.get(str);
            writeStringGet(index);
            return;
        }
        if (isKey) {
            if (options == PsonOptions.ProgressiveKeys) {
                initialDictionary.put(str, initialDictionary.size());
                writeStringAdd(str);
            } else
                super.writeString(str);
        } else {
            if (options == PsonOptions.ProgressiveValues) {
                initialDictionary.put(str, initialDictionary.size());
                writeStringAdd(str);
            } else
                super.writeString(str);
        }
    }

    /**
     * Object类型转Map
     *
     * @param obj
     * @return
     */
    private static Map<String, Object> ObjectToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor property : propertyDescriptors) {
                String key = property.getName();
                // 过滤class属性
                if (!key.equals("class")) {
                    // 得到property对应的getter方法
                    Method getter = property.getReadMethod();
                    Object value = getter.invoke(obj);
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return map;
    }
}