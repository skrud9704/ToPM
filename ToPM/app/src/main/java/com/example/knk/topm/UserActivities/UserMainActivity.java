package com.example.knk.topm.UserActivities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.MovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.knk.topm.BackPressedHandler;
import com.example.knk.topm.CustomAdapters.NormalScheduleListAdapter;
import com.example.knk.topm.JoinActivity;
import com.example.knk.topm.Object.Movie;
import com.example.knk.topm.Object.MovieSchedule;
import com.example.knk.topm.Object.User;
import com.example.knk.topm.R;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class UserMainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{

    private BackPressedHandler backPressedHandler;  // 뒤로가기 핸들러

    User user;                              // 현재 로그인 한 유저

    private ListView dayScheduleList;
    private ArrayList<MovieSchedule>[] scheduleData;
    private NormalScheduleListAdapter adapter;
    private ArrayList<String>[] keyData;

    /* 상단뷰를 위한 변수 */
    private TextView dateTextView;          // 현재 이동돼있는 날짜를 보여주는 텍스트뷰
    private Button prevBtn;                 // 이전날짜로 가는 버튼
    private Button nextBtn;                 // 이후날짜로 가는 버튼

    private Date currentDate;               // 오늘 날짜
    public int dateCount;
    public String strDate;

    // 데이터베이스 관련
    private FirebaseDatabase firebaseDatabase;          // firebaseDatabase
    private DatabaseReference rootReference;            // rootReference
    private DatabaseReference movieReference;           // movieReference
    final private String SCHEDULE_REF = "schedule";     // 스케쥴을 레퍼런스할 노드 이름
    final private String MOVIE_REF = "movie";           // 영화를 레퍼런스할 노드 이름
    private boolean isEnoughAge;
    private int movieRate;

    // 상수
    final int FUTURE_DATE = 4;                          // 미래 날짜
    // 올해 저장 - 나이제한 대비
    final int THIS_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_main);
        backPressedHandler = new BackPressedHandler(this,1);    // 뒤로가기 핸들러 초기화
        init();                                         //초기화
    }

    // 뒤로가기 눌렀을 때 처리하는 함수
    @Override
    public void onBackPressed() {
        backPressedHandler.onBackPressed();
    }

    public void init() {

        Intent intent = getIntent();
        user = (User) intent.getSerializableExtra("user");

        // 위젯 초기화
        dayScheduleList = findViewById(R.id.dayScheduleList);
        scheduleData = new ArrayList[FUTURE_DATE];
        for(int i=0; i<FUTURE_DATE; i++) {                      //오늘부터 최대 3일후까지만 생성
            scheduleData[i] = new ArrayList<>();
        }
        keyData = new ArrayList[FUTURE_DATE];
        for(int i=0; i<FUTURE_DATE; i++) {                      //오늘부터 최대 3일후까지만 생성
            keyData[i] = new ArrayList<>();
        }

        // 데이터베이스 초기화
        firebaseDatabase = FirebaseDatabase.getInstance();
        rootReference = firebaseDatabase.getReference(SCHEDULE_REF);
        movieReference = firebaseDatabase.getReference(MOVIE_REF);

        // 데이터베이스에서 스케쥴 정보 받아오기
        getScheduleFromDB();

        adapter = new NormalScheduleListAdapter(this, R.layout.schedule_list_adpater_row2, scheduleData[0]);
        dayScheduleList.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        // 상단 텍스트뷰, 버튼
        dateTextView = findViewById(R.id.dateTextView);
        prevBtn = findViewById(R.id.prevBtn);
        prevBtn.setEnabled(false);              // 초기화면은 무조건 오늘날짜이므로 이전 날짜 버튼 비활성화하기
        nextBtn = findViewById(R.id.nextBtn);

        // 오늘 날짜 계산
        currentDate = new Date(System.currentTimeMillis());         // 시스템으로부터 받아온 오늘 날짜를 Date형으로 저장
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일");   // 날짜 포맷
        strDate = sdf.format(currentDate);                          // 오늘 날짜를 날짜 포맷에 맞게 변형
        dateTextView.setText(strDate);                              // 오늘 날짜로 설정한 날짜를 보여주는 텍스트뷰에 setText

        // 변수 초기화
        dateCount = 0;

        // 리스트뷰 클릭 이벤트
        dayScheduleList.setOnItemClickListener(this);
    }

    // 데이터베이스에서 스케쥴 정보 받아오기
    public void getScheduleFromDB(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일");           // 스케쥴 DB의 KEY 형태
        scheduleData = new ArrayList[FUTURE_DATE];                  // 스케쥴정보를 날짜별로 저장할 arrayList 객체 배열

        for(int i = 0 ;i<FUTURE_DATE;i++)
            scheduleData[i] = new ArrayList<>();

        Calendar today = Calendar.getInstance();                    // 오늘 시간날짜정보 저장
        today.add(Calendar.DATE,-1);                        // 하루 빼기 - for문에서 하나씩 더할 것이므로 그래야 오늘날짜부터 시작할 수 있음.

        for(int i=0; i<FUTURE_DATE; i++) {
            today.add(Calendar.DATE, 1);                    // 날짜 하루씩 더하기 for문순서대로돌면서, 오늘,내일,2일뒤,3일뒤 계산
            String strDate = sdf.format(today.getTime());           // yyyy년 MM월 dd일의 포맷으로 바꿔 스트링형태로 저장. - DB의 key형태
            final int index = i;

            // 스케줄 데이터베이스 변경 이벤트 핸들러
            rootReference.child(strDate).addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    MovieSchedule newSchedule = dataSnapshot.getValue(MovieSchedule.class); // 새로 추가된 스케줄 받아옴
                    scheduleData[index].add(newSchedule);                                   // 리스트 뷰에 갱신
                    keyData[index].add(dataSnapshot.getKey());
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });

        }
    }

    // n일 후 날짜 스트링 반환
    public String dateCalculator(int n) {
        String str;
        Calendar today = Calendar.getInstance();
        today.add(Calendar.DATE, n);
        Date future = today.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일");
        str = sdf.format(future);
        return str;
    }

    public void showMyBookingClick(View view) {
        Intent intent = new Intent(this, MyBookingListActivity.class);
        intent.putExtra("user", user);
        intent.putExtra("date", strDate);
        startActivity(intent);
    }

    // 이전 날짜 버튼 클릭 리스너
    public void prevBtnClick(View view) {
        // 미래 날짜를 보고 있을 때만 동작하도록
        if(dateCount != 0) {
            dateCount--; // 하루 감소
            nextBtn.setEnabled(true); // 다음 버튼 활성화
            String str = dateCalculator(dateCount);
            dateTextView.setText(str);

            if(dateCount == 0) // 오늘 날짜에 도달
                prevBtn.setEnabled(false);  // 이전버튼 비활성화
            // 어댑터 새로 정의
            adapter = new NormalScheduleListAdapter(this, R.layout.schedule_list_adpater_row2, scheduleData[dateCount]);
            dayScheduleList.setAdapter(adapter); // 어댑터 새로 설정
            adapter.notifyDataSetChanged(); // 변경 통지
        }
    }

    // 이후 날짜 버튼 클릭 리스너
    public void nextBtnClick(View view) {
        // 가장 미래 날짜를 보고 있으면 안됨
        if(dateCount < FUTURE_DATE) {
            dateCount++; // 하루 증가
            prevBtn.setEnabled(true); // 다음 버튼 활성화
            String str = dateCalculator(dateCount);
            dateTextView.setText(str);

            if(dateCount == FUTURE_DATE - 1) // 가장 미래 날짜에 도달
                nextBtn.setEnabled(false);

            // 어댑터 새로 정의
            adapter = new NormalScheduleListAdapter(this, R.layout.schedule_list_adpater_row2, scheduleData[dateCount]);
            dayScheduleList.setAdapter(adapter); // 어댑터 새로 설정
            adapter.notifyDataSetChanged(); // 변경 통지
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        // 이미 매진 된 회차가 아닌 경우에만 BookMovieActivity로 전환됨
        // 여기 해야됩닏당

        // 방법 1)
        final String key = keyData[dateCount].get(position); // 클릭한 위치의 위치의 키를 받아오자
        final String date = dateCalculator(dateCount);

        // 현재 클릭한 위치의 영화스케쥴 정보를 가져온다
        MovieSchedule ms = (MovieSchedule) parent.getItemAtPosition(position);
        // 선택한 영화의 제목 정보 저장
        final String selectedMovie = ms.getMovieTitle();
        // 전역변수로 선언해둔 THIS_YEAR과 사용자의 생년정보를 이용해 사용자의 나이를 계산한다.
        final int user_age = THIS_YEAR - user.getBirth().getYear();

        // 사용자가 예매를 할 수 있는 나이인지 검사하는 변수
        isEnoughAge = false;
        // 영화DB에 리스너를 달아 영화의 등급정보를 가져온다.
        movieReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot data : dataSnapshot.getChildren()){
                    Movie movie = data.getValue(Movie.class);
                    if(isEnoughAge)
                        break;
                    // 선택한 영화의 제목과 같은 정보를 찾아 등급정보를 저장한다.
                    if(movie.getTitle().equals(selectedMovie)){
                        movieRate = movie.getRating();
                        // Toast.makeText(getApplicationContext(),rate+"",Toast.LENGTH_SHORT).show();
                        // 일치하는 그 영화에 대해 사용자의 나이가 등급보다 많거나 전체상영가의 영화라면
                        // isEnoughAge를 갱신한다.
                        if(user_age>movieRate || movieRate==-1){
                            isEnoughAge = true;
                        }
                    }
                }
                if(isEnoughAge){
                    // 다음 액티비티로 정보를 전송한다.
                    Intent intent = new Intent(getApplicationContext(), BookMovieActivity.class);
                    intent.putExtra("user", user);  // 현재 로그인 유저
                    intent.putExtra("key", key);    // 데이터베이스 접근 키
                    intent.putExtra("date", date);
                    startActivity(intent);
                }else{
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(UserMainActivity.this);  //context는 직접 명시해야 함.
                    alertBuilder.setTitle("예매 불가!");
                    alertBuilder.setMessage("고객님의 나이는 만 "+user_age+"세로, 해당 영화의 상영등급 만 "+movieRate+"세 미만 제한 대상입니다.");
                    alertBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).show();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });



    }

}
