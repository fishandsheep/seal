package org.fisheep.logic;

import io.javalin.http.Context;
import org.fisheep.bean.Db;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.StorageManagerFactory;

/**
 * @author BigOrange
 */
public class DbManager {

    public static void add(Context ctx) throws SealException {
        var db = ctx.bodyAsClass(Db.class);
        var data = StorageManagerFactory.data();
        boolean b = data.dbs().all().stream().anyMatch(db1 -> db1.getId().equals(db.getId()));
        if (b) {
            throw new SealException(ErrorEnum.MYSQL_CONNECTION_EXIST);
        }
        DbFactory.getDbVersion(db);
        data.dbs().add(db);
        db.setPassword(null);
        ctx.json(new Result(db));
    }

    public static void delete(Context ctx) {
        var id = Integer.parseInt(ctx.pathParam("id"));
        StorageManagerFactory.data().dbs().delete(id);
        ctx.json(new Result());
    }

    public static void update(Context ctx) throws SealException {
        var id = Integer.parseInt(ctx.pathParam("id"));
        var db = ctx.bodyAsClass(Db.class);
        DbFactory.getDbVersion(db);
        var data = StorageManagerFactory.data();
        boolean b = data.dbs().all().stream().anyMatch(db1 -> db1.getId().equals(db.getId()));
        if (b) {
            throw new SealException(ErrorEnum.MYSQL_CONNECTION_EXIST);
        }
        data.dbs().update(id, db);
        db.setPassword(null);
        StorageManagerFactory.data().dbs().update(id, db);
        ctx.json(new Result());
    }

    public static void one(Context ctx) {
        var id = Integer.parseInt(ctx.pathParam("id"));
        Db one = StorageManagerFactory.data().dbs().one(id);
        one.setPassword(null);
        ctx.json(new Result(one));
    }

    public static void all(Context ctx) {
        ctx.json(new Result(StorageManagerFactory.data().dbs().all()));
    }

}
