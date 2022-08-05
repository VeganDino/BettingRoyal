import React from "react";
import axios from "axios";
import { useEffect, useState } from "react";
import { TextField } from "@material-ui/core";

export default function Vault() {
  const [vaultmoney, setVaultmoney] = useState();
  const [depositamount, setDepositamount] = useState();
  const [withdrawamount, setWithdrawamount] = useState();
  const [depositstatus, setDepositstatus] = useState("");
  const [withdrawstatus, setWithdrawstatus] = useState("");
  useEffect(() => {
    axios
      .get("http://localhost:8080/api/vault", {
        headers: {
          "Content-Type": "application/json",

          Authorization: window.localStorage.getItem("accessToken"),
        },
      })
      .then((response) => {
        console.log("vault balance = " + response.data);
        setVaultmoney(response.data);
      })
      .catch((error) => {
        console.log(error);
      });
  }, []);

  function Deposit() {
    console.log("입금", depositamount);
    axios
      .put("http://localhost:8080/api/vault/update", depositamount, {
        headers: {
          "Content-Type": "application/json",

          Authorization: window.localStorage.getItem("accessToken"),
        },
      })
      .then((response) => {
        console.log("vault balance = " + response.data.userVault);
        setVaultmoney(response.data.userVault);
        setDepositstatus(depositamount + "루비 입금 완료");
      })
      .catch((error) => {
        if (error.response.data.error == 10) console.log("보유량 초과");
        setDepositstatus("보유량 초과");
      });
  }

  function Withdraw() {
    console.log("출금" + withdrawamount);
    axios
      .put("http://localhost:8080/api/vault/update", withdrawamount * -1, {
        headers: {
          "Content-Type": "application/json",

          Authorization: window.localStorage.getItem("accessToken"),
        },
      })
      .then((response) => {
        console.log("vault balance = " + response.data.userVault);
        setVaultmoney(response.data.userVault);
        setWithdrawstatus(withdrawamount + "루비 출금 완료");
      })
      .catch((error) => {
        if (error.response.data.error == 20) {
          console.log("금고량 초과");
          setWithdrawstatus("금고 보유량 초과");
        }
      });
  }

  return (
    <div>
      <p>현재 금고 잔액</p>
      <p>{vaultmoney}</p>

      <TextField
        onChange={(e) => {
          setDepositamount(e.target.value);
        }}
        autoFocus
      ></TextField>
      <button onClick={Deposit}>입금</button>
      <p>{depositstatus}</p>
      <TextField
        onChange={(e) => {
          setWithdrawamount(e.target.value);
        }}
      ></TextField>
      <button onClick={Withdraw}>출금</button>
      <p>{withdrawstatus}</p>
    </div>
  );
}