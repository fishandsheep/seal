package org.fisheep.bean.data;

import org.eclipse.serializer.reference.Lazy;
import org.fisheep.bean.SqlStatement;
import org.fisheep.bean.Struct;

import java.util.HashMap;
import java.util.List;

/**
 * @author BigOrange
 */
public class Structs {

    private Lazy<HashMap<SqlStatement, List<Struct>>> map;

    private void add() {

    }
}
