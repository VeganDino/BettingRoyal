import React from "react";
import { useEffect, useState } from "react";
import { Grid, Box, Toolbar, Container, Paper } from "@mui/material";
import axios, { Axios } from "axios";
import Button from "@mui/material/Button";
import DeleteIcon from "@mui/icons-material/Delete";
import { DataGrid } from "@mui/x-data-grid";

export default function Friends(props) {
  const [rows, setRows] = useState("");

  useEffect(() => {
    axios
      .get("/api/friends", {
        headers: {
          Authorization: window.localStorage.accessToken,
          "Content-Type": "application/json",
        },
      })
      .then((response) => {
        console.log(response.data);
        setRows(response.data);
      })
      .catch((error) => {
        console.log(error);
      });
  }, []);

  async function deleteFriendBtn(params) {
    console.log(params.row.friendId);
    await deleteFriend(params);
    await findFriend();
  }

  async function deleteFriend(params) {
    await axios
      .delete("/api/friends", {
        params: { getFriendToUserId: params.row.friendId },
        headers: {
          Authorization: window.localStorage.accessToken,
          "Content-Type": "application/json",
        },
      })
      .then((response) => {
        console.log(response.data);
      })
      .catch((error) => {
        console.log(error);
      });
  }

  async function findFriend() {
    await axios
      .get("/api/friends", {
        headers: {
          Authorization: window.localStorage.accessToken,
          "Content-Type": "application/json",
        },
      })
      .then((response) => {
        console.log(response.data);
        setRows(response.data);
      })
      .catch((error) => {
        console.log(error);
      });
  }

  const columns = [
    {
      field: "nickname",
      headerName: "닉네임",
      width: 150,
      editable: false,
    },
    {
      field: "ruby",
      headerName: "보유 루비",
      width: 150,
      editable: false,
    },
    {
      field: "friendId",
      headerName: "친구삭제",
      width: 150,
      editable: false,
      renderCell: (params) => (
        <strong>
          <Button
            startIcon={<DeleteIcon />}
            onClick={() => {
              deleteFriendBtn(params);
            }}
          >
            친구삭제
          </Button>
        </strong>
      ),
    },
  ];

  let friendLists = (
    <Grid>
      <Box sx={{ height: 400, width: "100%" }}>
        <DataGrid
          rows={rows}
          columns={columns}
          pageSize={5}
          rowsPerPageOptions={[5]}
          disableSelectionOnClick
        />
      </Box>
    </Grid>
  );

  return friendLists;
}
