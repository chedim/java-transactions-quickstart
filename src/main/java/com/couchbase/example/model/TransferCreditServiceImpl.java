package com.couchbase.example.model;


import java.util.function.Consumer;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionGetResult;
import com.couchbase.transactions.Transactions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TransferCreditServiceImpl implements TransferCreditService {


    @Autowired
    private Transactions transactions;
    @Autowired
    private Bucket bucket;


    /**
     * IMPORTANT: The validation is made after changing both documents to show how they are reverted using transactions.
     */
    public void transferCredit(String sourceUser, String targetUser, int creditsToTransfer) {
      Consumer<AttemptContext> transactionLogic = (Consumer<AttemptContext>) ctx -> {
        //Load both users
        TransactionGetResult u1DocTx = ctx.get(bucket.defaultCollection(), sourceUser);
        TransactionGetResult u2DocTx = ctx.get(bucket.defaultCollection(), targetUser);

        //convert them to JsonObjects
        JsonObject u1Doc = u1DocTx.contentAs(JsonObject.class);
        JsonObject u2Doc = u2DocTx.contentAs(JsonObject.class);

        int user1Balance = getCredits(u1Doc) - creditsToTransfer;
        int user2Balance = getCredits(u2Doc) + creditsToTransfer;
        //update their credits
        u1Doc.put("credits", user1Balance);
        u2Doc.put("credits", user2Balance);

        //save both users
        ctx.replace(u1DocTx, u1Doc);
        ctx.replace(u2DocTx, u2Doc);

        if(user1Balance < 0) {
            throw new IllegalStateException("User can't have a negative balance");
        }
      };
      transactions.run(transactionLogic);
    }

    private int getCredits(JsonObject obj) {
        return obj.getInt("credits") == null ? 0 : obj.getInt("credits");
    }
}
