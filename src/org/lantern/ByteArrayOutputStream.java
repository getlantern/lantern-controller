/*
 * The VObject AppEngine Java-IO is an open source project that porting gaevfs 
 * to java.io API to enable java.io classes support in Google App Engine (GAE). 
 * Copyright (C) VObject.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author: Lim Chee Kin (limcheekin@vobject.com)
 *
 */
package org.lantern;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#close()
     */
    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
        super.close();
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#reset()
     */
    @Override
    public synchronized void reset() {
        // TODO Auto-generated method stub
        super.reset();
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#size()
     */
    @Override
    public synchronized int size() {
        // TODO Auto-generated method stub
        return super.size();
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#toByteArray()
     */
    @Override
    public synchronized byte[] toByteArray() {
        // TODO Auto-generated method stub
        return super.toByteArray();
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#toString()
     */
    @Override
    public synchronized String toString() {
        // TODO Auto-generated method stub
        return super.toString();
    }


    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#toString(java.lang.String)
     */
    @Override
    public synchronized String toString(String charsetName)
            throws UnsupportedEncodingException {
        // TODO Auto-generated method stub
        return super.toString(charsetName);
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#write(byte[], int, int)
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) {
        // TODO Auto-generated method stub
        super.write(b, off, len);
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#write(int)
     */
    @Override
    public synchronized void write(int b) {
        // TODO Auto-generated method stub
        super.write(b);
    }

    /* (non-Javadoc)
     * @see java.io.ByteArrayOutputStream#writeTo(java.io.OutputStream)
     */
    @Override
    public synchronized void writeTo(OutputStream out) throws IOException {
        // TODO Auto-generated method stub
        super.writeTo(out);
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#flush()
     */
    @Override
    public void flush() throws IOException {
        // TODO Auto-generated method stub
        super.flush();
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#write(byte[])
     */
    @Override
    public void write(byte[] b) throws IOException {
        // TODO Auto-generated method stub
        super.write(b);
    }

    /**
     * 
     */
    public ByteArrayOutputStream() {
        super();
        // TODO Auto-generated constructor stub
    }

    /**
     * @param size
     */
    public ByteArrayOutputStream(int size) {
        super(size);
        // TODO Auto-generated constructor stub
    }

}