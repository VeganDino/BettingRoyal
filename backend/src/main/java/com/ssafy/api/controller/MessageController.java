package com.ssafy.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ssafy.api.service.RoomService;
import com.ssafy.api.service.UserService;
import com.ssafy.db.entity.*;
import com.ssafy.db.repository.GamePlayerRepository;
import com.ssafy.db.repository.GameRepository;
import com.ssafy.db.repository.PlayerCardSetInGameRepository;
import com.ssafy.db.repository.RoomSizeRepository;
import com.ssafy.db.entity.GameMessage;
import com.ssafy.db.entity.GamePlayer;
import com.ssafy.db.entity.PlayerCardSetInGame;
import com.ssafy.db.entity.User;
import com.ssafy.db.repository.*;
import com.ssafy.db.vo.MessageType;
import com.ssafy.api.service.GameInfoService;
import com.ssafy.api.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Controller
@RequiredArgsConstructor
@Log4j2
/**
 * @MessageMapping을 통해 Websocket으로 들어오는 메시지 발행을 처리합니다. 클라이언트에서는 prefix를 붙여서
 *                  /pub/game/message로 발행 요청을 하면 Controller가 해당 메시지를 받아 처리합니다.
 *                  메시지가 발행되면 /sub/game/room/{roomId}로 메시지를 send 하는 것을 볼 수 있는데
 *                  클라이언트에서는 해당 주소를(/sub/game/room/{roomId}) 구독(subscribe)하고 있다가
 *                  메시지가 전달되면 화면에 출력하면 됩니다. 여기서 /sub/game/room/{roomId}는 채팅룸을
 *                  구분하는 값이므로 pub/sub에서 Topic의 역할이라고 보면 됩니다.
 */
//기존의 WebSocketHandler가 했던 역할을 대체한다!!
public class MessageController {

	// 메세지를 보내는 양식을 지정해둔 template
	// 이걸 사용하면 편하게 메세지를 클라이언트쪽으로 보낼 수 있음.
	@Autowired
	private final SimpMessagingTemplate template;
	@Autowired
	private GameService gameService;
	@Autowired
	private GameInfoService gameInfoService;
	@Autowired
	private RoomSizeRepository roomSizeRepository;
	@Autowired
	private PlayerCardSetInGameRepository playerCardSetInGameRepository;
	@Autowired
	private GameRepository gameRepository;
	@Autowired
	private GamePlayerRepository gamePlayerRepository;
	@Autowired
	private RoomService roomService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserService userService;
	Gson gson = new Gson();

	// 클라이언트에서 메세지가 날라왔다.
	@MessageMapping(value = "/game/message")
	// headerAccessor는 소켓서버의 주인ID를 확인하기 위해서 사용
	public void message(GameMessage message, SimpMessageHeaderAccessor headerAccessor) throws InterruptedException {
		log.info(message.getType());

		if (message.getType().equals(MessageType.ENTER)) {
			log.info(headerAccessor.getUser().getName());


			//방이 만들어졌을때
			if(!roomSizeRepository.isRoomExist(message.getRoomId())){
				roomSizeRepository.plusPlayerCnt(message.getRoomId());
				gamePlayerRepository.addGamePlayer(message, headerAccessor.getUser().getName());

			}
			else{
				//6명 초과면 거절
				if(roomSizeRepository.getRoomSize(  message.getRoomId() ) >=6 ){

				}else{
					// 방에 들어오면 player를 한명 올려준다.
					roomSizeRepository.plusPlayerCnt(message.getRoomId());

					// gamePlayer에 넣어준다.
					gamePlayerRepository.addGamePlayer(message, headerAccessor.getUser().getName());

//					String players =gson.toJson(gamePlayerRepository.getGamePlayer(message.getRoomId()));
					//				players.add(gamePlayerRepository.getGamePlayer(message.getRoomId()));
					message.setMessage("새로운 플레이어가 게임에 입장하셨습니다. / name : " + headerAccessor.getUser().getName());
					//모든 플레이어 정보 전송
					message.setPlayerInfo(gamePlayerRepository.getGamePlayer(message.getRoomId()));
					template.convertAndSend("/sub/game/room" + message.getRoomId(), message);

					}
			}


		}

		// 게임이 시작버튼이 눌렸다.
		if (message.getType().equals(MessageType.START)) {
			// 게임이 시작되었습니다. 메시지 주기
			message.setMessage("게임이 시작되었습니다.");
			template.convertAndSend("/sub/game/room" + message.getRoomId(), message);

			// 공용카드 뽑기
			int roomId = message.getRoomId();
			Random r = new Random();
			int groundCard1 = r.nextInt(39);
			int groundCard2 = r.nextInt(39);
			while (groundCard1 == groundCard2) {
				groundCard2 = r.nextInt(39);
			}

			// tb_game 생성 -> 공용카드 넣기
			int gameId = gameService.createGame(roomId, groundCard1, groundCard2);
			log.info(gameId);
			message.setGameId(gameId);

			message.setMessage("공통카드 : " + groundCard1 + ", " + groundCard2);
			message.setType(MessageType.GROUNDCARD);
			template.convertAndSend("/sub/game/room" + roomId, message);

			//방 정보 가져와서 기본 배팅 금액 가져오기
			Room room = roomService.getRoom(roomId);
			int bettingUnit = room.getRoomBettingUnit();

			// 사람들한테 bettingUnit만큼 돈 내게하기
			message.setMessage("기본 베팅금액을 베팅하십시오.");
			message.setType(MessageType.UNITBETTING);
			template.convertAndSend("/sub/game/room" + roomId, message);

			//이 방에서 게임하고있는 플레이어들
			List<GamePlayer> thisGamePlayer = gamePlayerRepository.getGamePlayer(message.getRoomId());

			// 게임 방 안의 모든 유저 돈 내고 게임 한 판 추가
			for(GamePlayer gp : thisGamePlayer){
				User temp = gp.getUser();
				temp.setUserRuby(temp.getUserRuby()-bettingUnit);
				temp.setUserGameCount(temp.getUserGameCount()+1);
				userService.modifyUser(temp);
				//게임 유저들 게임 배팅 정보 업데이트
				gp.setMaxBetting(gp.getMaxBetting()+bettingUnit);
				gp.setMyBetting(gp.getMyBetting()+bettingUnit);
			}

			//todo
			//밑에 있는거 하나씩 해당하는거 디비 업데이트나 게임플레이어 정보 업데이트해주기!!!

			// 카드뭉치에 개인 카드 넣기
			// 방인원 수 구하기
			int roomSize = roomSizeRepository.getRoomSize(roomId);
			// 그 인원만큼 카드 만들어서 뭉치에 넣기
			List<Integer> cardSet = new ArrayList<>();
			for (int i = 0; i < roomSize; i++) {
				while (true) {
					// 39장 중 랜덤으로 한장 뽑는다.
					int playerCard = r.nextInt(39);
					// 새로운 카드면 카드뭉치에 넣고 다음사람 카드 뽑는다.
					if (playerCard != groundCard1 && playerCard != groundCard2 && !cardSet.contains(playerCard)) {
						cardSet.add(playerCard);
						break;
					}
				}
			} // 카드뽑기 for문 종료
			PlayerCardSetInGame thisGameCardSet = new PlayerCardSetInGame();
			thisGameCardSet.setCardSet(cardSet);

			// 게임의 개인 카드뭉치를 서버에서 갖고있는다.
			playerCardSetInGameRepository.addPlayerCardSet(gameId, thisGameCardSet);

//			// 끝났고 이제 카드달라고 각 요청하라함.
//			message.setMessage("");
//			message.setType(MessageType.MAKECARDSET);
//			template.convertAndSend("/sub/game/room" + roomId, message);


			//이 방에있는 플레이어들한테 각자 메시지 보내주자
			int idx = 0;
			for(GamePlayer gp : thisGamePlayer){
				//서버에서 저장하는 이 방에서 게임하는 플레이어들의 게임 아이디를 저장해주자
				gp.setGameId(gameId);
				//서버에서 저장하는 이 방에서 게임하는 플레이어들에게 방의 기본 베팅도 저장해주자
				gp.setBattingUnit(bettingUnit);

				log.info(idx);
				String content ="";
				//카드뭉치에서 자기것만 빼고 보내준다.
				int myCard = cardSet.get(idx);
				for(int i=0; i< thisGamePlayer.size(); i++){
					if(idx==i)
						continue;
					content+=cardSet.get(i)+" ";
				}
				idx++;

				message.setMessage(content);
				message.setType(MessageType.GETMYCARD);

				// tb_game_info생성 후 게임id, 플레이어id, 개인카드 입력
				gameInfoService.createGameInfo(gameId, 1, myCard);

				template.convertAndSendToUser(gp.getSessionId(), "sub/game/room" + message.getRoomId(), message);
			}

			// 10초 기다리기(자유롭게 대화)
			Thread.sleep(10000);
			log.info("10초끝");

			// Turn부터 시작하기
			// 마이턴 찾아서 너 해!!
			for (GamePlayer pg : thisGamePlayer) {
				// 얘가 첨이다
				if (pg.isMyTurn()) {
					String firstUserId = pg.getSessionId();
					message.setMessage("당신의 차례입니다. 배팅을 하세요");
					message.setType(MessageType.FIRSTBETTING);
					template.convertAndSendToUser(firstUserId, "sub/game/room" + message.getRoomId(), message);
				}
			}
		}

		//********************배팅관련 처리********************
		// 콜이 들어왔을 때
		if (message.getType().equals(MessageType.CALL)) {
			int roomId =  message.getRoomId();
			int gameId = message.getGameId();
			// call한 사람 sessionId
			String userInfo = headerAccessor.getUser().getName();
			String userNickname = message.getSenderNickName();

			// 이 사람이 콜 누른걸 알려줘야댐.
			message.setMessage(userInfo+"플레이어가 콜을 하셨습니다.");
			message.setType(MessageType.PLAYERCALL);
			template.convertAndSend("/sub/game/room" + roomId, message);


			// GamePlayer에서 myBetting plus 해주고 callBettingCnt 돌려주기
			int callBettingCnt = gamePlayerRepository.callBetting(roomId, userInfo);

			// tb_GameInfo에서 rubyGet minus해주기
			gameInfoService.callBetting(gameId, userNickname, callBettingCnt);

			// tb_User에서 ruby minus해주기
			User user = userRepository.findByUserNickname(userNickname);
			user.setUserRuby(user.getUserRuby()-callBettingCnt);
			userService.modifyUser(user);

			// 끝났는지체크


			// 다음턴으로 넘기고 그사람한테 배팅하라고 알려줌
		}

		// 다이가 들어왔을 때
		if (message.getType().equals(MessageType.DIE)) {
			int roomId =  message.getRoomId();
			String userInfo = headerAccessor.getUser().getName();
			gamePlayerRepository.dieBetting(roomId, userInfo);

			// 끝났는지체크


			// 다음턴으로 넘기고 그사람한테 배팅하라고 알려줌
		}

		// 레이즈가 들어왔을 때
		if (message.getType().equals(MessageType.RAISE)) {
			int roomId =  message.getRoomId();
			String userNickname = message.getSenderNickName();

			// raise한 사람 sessionId
			String userInfo = headerAccessor.getUser().getName();
			int gameId = message.getGameId();

			// 이 사람이 레이즈 누른걸 알려줘야댐.
			message.setMessage(userInfo+"플레이어가 레이즈를 하셨습니다.");
			message.setType(MessageType.PLAYERRAISE);
			template.convertAndSend("/sub/game/room" + roomId, message);

			//얼마나 raise?
			int raiseCnt = Integer.parseInt(message.getMessage());

			//(Server) GamePlayer에서 myBetting plus 해주고 raiseBettingCnt 돌려주기
			int raiseBetting = gamePlayerRepository.raiseBetting(roomId, userInfo, raiseCnt);

			//(DB) GameInfo에서 rubyGet minus해주기
			gameInfoService.raiseBetting(gameId, userInfo, raiseBetting);

			// tb_User에서 ruby minus해주기
			User user = userRepository.findByUserNickname(userNickname);
			user.setUserRuby(user.getUserRuby()-raiseBetting);
			userService.modifyUser(user);

			// 끝났는지체크


			// 다음턴으로 넘기고 그사람한테 배팅하라고 알려줌
		}

		// 올인이 들어왔을 때
		if (message.getType().equals(MessageType.ALLIN)) {
			//이거 요청이 맞는 순서인 유저한테 들어온건지 확인
			//roomId와 sessiond로 GamePlayer를 찾아서 걔의 myTurn이 true인지 확인


			String userInfo = headerAccessor.getUser().getName();
			int gameId = message.getGameId();
			String userNickname = message.getSenderNickName();

			//(Server) GamePlayer에서 myBetting plus 해주고 allInBettingCnt 돌려주기
			int allInBettingCnt = gamePlayerRepository.allInBetting(message, userInfo);

			//(DB) GameInfo에서 allInBettingCnt만큼 rubyGet minus해주기
			gameInfoService.allInBetting(gameId, message.getSenderNickName(), allInBettingCnt);

			// tb_User에서 ruby minus해주기
			User user = userRepository.findByUserNickname(userNickname);
			user.setUserRuby(user.getUserRuby()-allInBettingCnt);
			userService.modifyUser(user);

			// 끝났는지체크
			checkFinish(message, headerAccessor);

			// 다음턴으로 넘기고 그사람한테 배팅하라고 알려줌
		}
		//***************************************************



		//********************나갔을때 확인********************
		// 나갔을때
		if (message.getType().equals(MessageType.EXIT)) {
			// 방에 나가면 player를 한명 내려준다.
			roomSizeRepository.minusPlayerCnt(message.getRoomId());

			//인원 0 되면 방 지우기
			if(roomSizeRepository.getRoomSize(message.getRoomId()) <=0) {
				roomService.deleteByRoomId(message.getRoomId());
				System.out.println("room deleted : " + message.getRoomId());
			}else{
				// gamePlayer에서 빼준다.
				//지운애가 방장이면 true 반환한다.
				boolean flag = gamePlayerRepository.deleteGamePlayer(message.getRoomId(), headerAccessor.getUser().getName());
				//방장 지웠으면 그 다음애 true
				if(flag) {
					gamePlayerRepository.getGamePlayer(message.getRoomId()).get(0).setMyTurn(true);
				}


				message.setMessage(message.getSenderNickName()+" 플레이어가 퇴장하셨습니다.");
				message.setPlayerInfo(gamePlayerRepository.getGamePlayer(message.getRoomId()));
				template.convertAndSend("/sub/game/room" + message.getRoomId(), message);

			}
		}

	}


	//다음 사람을 비교해서 게임 종료 여부와 다음 차례인 사람을 찾는 함수
	public void checkFinish(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
		//게임방에 참여중인 참가자들을 구한다
		List<GamePlayer> gp = gamePlayerRepository.getGamePlayer(message.getRoomId());

		//finishCnt와 dieCnt를 활용해서 게임이 끝났는지 확인
		//finishCnt = dieCnt + (userBetting==MaxBetting)Cnt
		int finishCnt = 0;
		int dieCnt = 0;
		for(GamePlayer player : gp){
			//플레이어의 베팅이 기본베팅이 아니면서 maxBetting이랑 같으면 finishCnt올려준다.
			if(player.getMyBetting()!=player.getBattingUnit()&&player.getMyBetting()==player.getMaxBetting()){
				finishCnt++;
				continue;
			}
			else if(player.isDie()){
				finishCnt++;
				dieCnt++;
				continue;
			}
			//하나라도 충족못하면 다음턴 진행해야된다.
			break;
		}

		//finishCnt가 gameSize거나
		//dieCnt가 gameSize-1이면 게임을 끝낸다.
		int gameSize = gp.size();
		//******************************게임 끝남!!!!******************************
		if(finishCnt==gameSize||dieCnt==gameSize-1){
			//누가 이겼는지 확인해보자.

			//메세지(Message.setMessage)에 누가 이겼는지 알려줘야 함


			//tb_gameInfo에 이긴사람은 루비획득+해주고 승리여부 이긴거 기록해줘야함.

			
			//tb_gameInfo에 진사람은 루비획득+해주고 승리여부 false 기록해줘야함.


			//tb_game에 승자 기록


			//이긴 user의 승수,루비 수 올려주기


			//gamePlayer의 요소들 초기화


			//이긴사람이 처음 시작하는걸로 바꿔주자.


			//끝났다고 알려줘~~!!
			message.setType(MessageType.GAMEEND);
			template.convertAndSend("/sub/game/room" + message.getRoomId(), message);
		}



		//*******************게임 계속할거야. 다음사람한테 턴 넘겨!!*******************
		else{
			//이번 플레이어의 myTurn은 false로 바꿔주고 다음번 애 true로 바꿔준다.


			//turnIdx++ 해주기
			message.setTurnIdx(message.getTurnIdx()+1);
			//어떤 행동을 했는지 확인!
			String bettingType = String.valueOf(message.getType());
			//RAISE했으면 얼만큼 레이즈했는지 알려줘야함. ex)RAISE 5
			if(bettingType.equals("RAISE")){
				bettingType+=(" " + message.getMessage());
			}
			message.setMessage(bettingType);
			message.setType(MessageType.NEXTTURN);

			//각자한테 마스킹해서 보내주기.
		}





	}

}
