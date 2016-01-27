/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.db.mongodb;

import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.opencb.opencga.catalog.db.mongodb.converters.GenericConverter;

import java.io.Closeable;
import java.util.Iterator;

/**
 * Created by imedina on 27/01/16.
 */
public class CatalogMongoDBIterator<E> implements Iterator<E>, Closeable {

    private MongoCursor mongoCursor;
    private GenericConverter<E, Document> converter;

    CatalogMongoDBIterator(MongoCursor mongoCursor, GenericConverter<E, Document> converter) { //Package protected
        this.mongoCursor = mongoCursor;
        this.converter = converter;
    }

    @Override
    public boolean hasNext() {
        return mongoCursor.hasNext();
    }

    @Override
    public E next() {
        Document next = (Document) mongoCursor.next();
        return converter.convertToDataModelType(next);
    }

    @Override
    public void close() {
        mongoCursor.close();
    }

}