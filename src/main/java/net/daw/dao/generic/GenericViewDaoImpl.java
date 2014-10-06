/*
 * Copyright (C) July 2014 Rafael Aznar
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.daw.dao.generic;

import net.daw.dao.publicinterface.ViewDaoInterface;
import net.daw.dao.publicinterface.MetaDaoInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import net.daw.bean.generic.GenericBeanImpl;
import net.daw.helper.FilterBean;

public class GenericViewDaoImpl<TIPO_OBJETO> extends GenericMetaDaoImpl<TIPO_OBJETO> implements ViewDaoInterface<TIPO_OBJETO>, MetaDaoInterface {

    public GenericViewDaoImpl(String view, Connection pooledConnection) throws Exception {
        super(view, pooledConnection);
    }

    @Override
    public int getPages(int intRegsPerPag, ArrayList<FilterBean> hmFilter) throws Exception {
        int pages;
        try {
            pages = oMysql.getPages(strView, intRegsPerPag, hmFilter);
            return pages;
        } catch (Exception e) {
            throw new Exception("GenericViewDaoImpl.getPages: Error: " + e.getMessage());
        }
    }

    @Override
    public int getCount(ArrayList<FilterBean> hmFilter) throws Exception {
        int pages;
        try {
            pages = oMysql.getCount(strView, hmFilter);
            return pages;
        } catch (Exception e) {
            throw new Exception("GenericViewDaoImpl.getCount: Error: " + e.getMessage());
        }

    }

    @Override
    public ArrayList<TIPO_OBJETO> getPage(int intRegsPerPag, int intPage, ArrayList<FilterBean> hmFilter, HashMap<String, String> hmOrder) throws Exception {
        Class<TIPO_OBJETO> tipo = (Class<TIPO_OBJETO>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        Method metodo_setId = tipo.getMethod("setId", Integer.class);
        ArrayList<Integer> arrId;
        ArrayList<TIPO_OBJETO> arrCliente = new ArrayList<>();
        try {
            arrId = oMysql.getPage(strView, intRegsPerPag, intPage, hmFilter, hmOrder);
            Iterator<Integer> iterador = arrId.listIterator();
            while (iterador.hasNext()) {
                Object oBean = Class.forName(tipo.getName()).newInstance();
                metodo_setId.invoke(oBean, iterador.next());
                arrCliente.add(this.get((TIPO_OBJETO) oBean));
            }
            return arrCliente;
        } catch (Exception e) {
            throw new Exception("GenericViewDaoImpl.getPage: Error: " + e.getMessage());
        }

    }

    private void parseValue(TIPO_OBJETO oBean, Method method, String strTipoParamMetodoSet, String strValor) throws Exception {
        switch (strTipoParamMetodoSet) {
            case "java.lang.Double":
                method.invoke(oBean, Double.parseDouble(strValor));
                break;
            case "java.lang.Integer":
                method.invoke(oBean, Integer.parseInt(strValor));
                break;
            case "java.lang.Boolean":
                if (Integer.parseInt(strValor) == 1) {
                    method.invoke(oBean, true);
                } else {
                    method.invoke(oBean, false);
                }
                break;
            case "java.util.Date":
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                method.invoke(oBean, format.parse(strValor));
                break;
            default:
                method.invoke(oBean, strValor);
                break;
        }
    }

    private TIPO_OBJETO fillForeign(TIPO_OBJETO oBean, Class<TIPO_OBJETO> tipo) throws NoSuchMethodException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, Exception {
        for (Method method : tipo.getMethods()) {
            if (!method.getName().substring(3).equalsIgnoreCase("id")) {
                if (method.getName().substring(0, 3).equalsIgnoreCase("set")) {
                    final Class<?> classTipoParamMetodoSet = method.getParameterTypes()[0];
                    if (method.getName().length() >= 5) {
                        if (method.getName().substring(3).toLowerCase(Locale.ENGLISH).substring(0, 4).equalsIgnoreCase("obj_")) {

                            //ojo: en los pojos, los id_ deben preceder a los obj_ del mismo objeto siempre!
                            String strAjena = method.getName().substring(3).toLowerCase(Locale.ENGLISH).substring(4);
                            Method metodo_getId_Ajena = tipo.getMethod("getId_" + strAjena);
                            strAjena = strAjena.substring(0, 1).toUpperCase(Locale.ENGLISH) + strAjena.substring(1);
                            //GenericDaoImplementation oAjenaDao = (GenericDaoImplementation) Class.forName("net.daw.dao." + strAjena + "Dao").newInstance();

                            Constructor c = Class.forName("net.daw.dao.implementation." + strAjena + "DaoImpl").getConstructor(Connection.class);
                            GenericTableDaoImpl oAjenaDao = (GenericTableDaoImpl) c.newInstance(connection);

                            GenericBeanImpl oAjenaBean = (GenericBeanImpl) Class.forName("net.daw.bean.implementation." + strAjena + "BeanImpl").newInstance();
                            int intIdAjena = (Integer) metodo_getId_Ajena.invoke(oBean);
                            oAjenaBean.setId(intIdAjena);
                            oAjenaBean = (GenericBeanImpl) oAjenaDao.get(oAjenaBean);
                            //String strDescription = oAjenaDao.getDescription((Integer) metodo_getId_Ajena.invoke(oBean));
                            method.invoke(oBean, oAjenaBean);
                        }
                    }
                }
            }
        }
        return oBean;
    }

    private TIPO_OBJETO fill(TIPO_OBJETO oBean, Class<TIPO_OBJETO> tipo, Method metodo_getId) throws NoSuchMethodException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, Exception {
        for (Method method : tipo.getMethods()) {
            if (!method.getName().substring(3).equalsIgnoreCase("id")) {
                if (method.getName().substring(0, 3).equalsIgnoreCase("set")) {
                    final Class<?> classTipoParamMetodoSet = method.getParameterTypes()[0];
                    if (method.getName().length() >= 5) {
                        if (!method.getName().substring(3).toLowerCase(Locale.ENGLISH).substring(0, 4).equalsIgnoreCase("obj_")) {
                            String strValor = oMysql.getOneFromTable(strView, method.getName().substring(3).toLowerCase(Locale.ENGLISH), (Integer) metodo_getId.invoke(oBean));
                            if (strValor != null) {
                                parseValue(oBean, method, classTipoParamMetodoSet.getName(), strValor);
                            }
                        }
                    } else {
                        String strValor = oMysql.getOneFromTable(strView, method.getName().substring(3).toLowerCase(Locale.ENGLISH), (Integer) metodo_getId.invoke(oBean));
                        if (strValor != null) {
                            parseValue(oBean, method, classTipoParamMetodoSet.getName(), strValor);
                        }
                    }

                }
            }
        }
        return oBean;
    }

    @Override
    public TIPO_OBJETO get(TIPO_OBJETO oBean) throws Exception {
        Class<TIPO_OBJETO> tipo = (Class<TIPO_OBJETO>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        Method metodo_getId = tipo.getMethod("getId");
        Method metodo_setId = tipo.getMethod("setId", Integer.class);
        if ((Integer) metodo_getId.invoke(oBean) > 0) {
            try {
                if (!oMysql.existsOne(strView, (Integer) metodo_getId.invoke(oBean))) {
                    metodo_setId.invoke(oBean, 0);
                } else {
                    oBean = fill(oBean, tipo, metodo_getId);
                    oBean = fillForeign(oBean, tipo);
                }
            } catch (Exception e) {
                throw new Exception("GenericViewDaoImpl.get: Error: " + e.getMessage());
            }
        } else {
            metodo_setId.invoke(oBean, 0);
        }
        return oBean;

    }

    @Override
    public ArrayList<String> getColumnsNames() throws Exception {
        ArrayList<String> alColumns = null;
        try {
            alColumns = oMysql.getColumnsName(strView);
        } catch (Exception e) {
            throw new Exception("GenericViewDaoImpl.getColumnsNames: Error: " + e.getMessage());
        }
        return alColumns;
    }

    @Override
    public ArrayList<String> getPrettyColumnsNames() throws Exception {
        ArrayList<String> alColumns = null;
        try {
            alColumns = oMysql.getPrettyColumns(strView);
        } catch (Exception e) {
            throw new Exception("GenericViewDaoImpl.getPrettyColumnsNames: Error: " + e.getMessage());
        }
        return alColumns;
    }

}
