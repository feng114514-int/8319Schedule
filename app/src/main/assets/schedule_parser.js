// 8319课表助手 - 从ChromeExtension复制

// 查找课表表格
function getTable() {
  let t = document.getElementById('kbtable');
  if (t) return t;
  
  let frames = document.querySelectorAll('iframe');
  for (let f of frames) {
    try {
      let doc = f.contentDocument || f.contentWindow.document;
      if (doc) {
        t = doc.getElementById('kbtable');
        if (t) return t;
      }
    } catch (e) {}
  }
  
  return null;
}

function getDoc(table) {
  if (!table) return document;
  return table.ownerDocument || document;
}

// 解析周次，如 "1-3,5,7-9周" -> [1,2,3,5,7,8,9]
function getWeeks(str) {
  if (!str) return [];
  let match = str.match(/([\d,\-]+)\(周\)/);
  if (!match) return [];
  
  let list = [];
  let ps = match[1].split(',');
  for (let p of ps) {
    if (p.includes('-')) {
      let [a, b] = p.split('-').map(Number);
      for (let i = a; i <= b; i++) list.push(i);
    } else {
      list.push(Number(p));
    }
  }
  return list;
}

// 解析节次，如 "[1-2节]" -> {s:1, e:2}
function getSections(str) {
  if (!str) return { s: 0, e: 0 };
  let match = str.match(/\[(\d+)-(\d+)节\]/);
  if (match) return { s: parseInt(match[1]), e: parseInt(match[2]) };
  return { s: 0, e: 0 };
}

// 解析单元格内的课程HTML
function parseCell(html) {
  if (!html) return [];
  
  let list = [];
  let arr = html.split(/-{10,}<br\s*\/?>?\s*/);
  
  for (let it of arr) {
    let text = it.replace(/^(<br\s*\/?>\s*)+/, '').trim();
    if (!text) continue;
    
    text = text.replace(/<span[^>]*>.*?<\/span>/gi, '');
    if (!text || text.includes('&nbsp;')) continue;
    
    let c = {
      name: '',
      teacher: '',
      weeks: [],
      sections: { s: 0, e: 0 },
      room: '',
      type: ''
    };
    
    let m1 = text.match(/^([^<]+)/);
    if (m1) c.name = m1[1].trim();
    
    let m2 = it.match(/<font title="老师">([^<]+)<\/font>/);
    if (m2) c.teacher = m2[1].trim();
    
    let m3 = it.match(/<font title="周次\(节次\)">([^<]+)<\/font>/);
    if (m3) {
      c.weeks = getWeeks(m3[1]);
      c.sections = getSections(m3[1]);
    }
    
    let m4 = it.match(/<font title="教室">([^<]+)<\/font>/);
    if (m4) c.room = m4[1].trim();
    
    let m5 = it.match(/<font name="xsks"[^>]*>\(([^)]+)\)/);
    if (m5) c.type = m5[1].trim();
    
    if (c.name) list.push(c);
  }
  
  return list;
}

// 解析整个课表
function parse() {
  let tab = getTable();
  if (!tab) return null;
  
  let doc = getDoc(tab);
  let out = {
    semester: '',
    courses: [],
    times: []
  };
  
  let sel = doc.getElementById('xnxq01id');
  if (sel) {
    let opt = sel.querySelector('option[selected]');
    if (opt) out.semester = opt.value;
  }
  
  let trs = tab.querySelectorAll('tbody > tr');
  let days = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
  let dayNames = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
  
  let period = 0;
  
  for (let tr of trs) {
    let th = tr.querySelector('th');
    if (!th) continue;
    
    let txt = th.textContent.trim();
    if (txt === '' || txt.includes('备注') || txt.includes('星期')) continue;
    
    let pm = txt.match(/第(.+?)大节/);
    let tm = txt.match(/(\d{2}:\d{2})-(\d{2}:\d{2})/);
    
    if (pm) {
      period++;
      if (tm) {
        out.times.push({
          period: period,
          name: `第${pm[1]}大节`,
          start: tm[1],
          end: tm[2]
        });
      }
    }
    
    let cells = tr.querySelectorAll('td');
    cells.forEach((cell, idx) => {
      if (idx >= days.length) return;
      
      let divs = cell.querySelectorAll('div');
      let div = null;
      
      for (let d of divs) {
        if (d.classList.contains('kbcontent') && !d.classList.contains('kbcontent1')) {
          div = d;
          break;
        }
      }
      
      if (!div) div = cell.querySelector('div.kbcontent');
      if (!div) return;
      
      let items = parseCell(div.innerHTML);
      
      items.forEach(c => {
        out.courses.push({
          ...c,
          day: days[idx],
          dayName: dayNames[idx],
          period: period
        });
      });
    });
  }
  
  return out;
}

// 转为Android需要的JSON格式
function formatForAndroid(res) {
  if (!res) {
    return JSON.stringify({
      success: false,
      error: "未找到课程表，请确保在教务系统课表页面"
    });
  }
  
  if (res.courses && res.courses.length > 0) {
    let list = [];
    
    for (let i = 0; i < res.courses.length; i++) {
      let c = res.courses[i];
      
      // 周次数组转字符串
      let weeksStr = c.weeks && c.weeks.length > 0 ? 
        c.weeks[0] + "-" + c.weeks[c.weeks.length - 1] : "";
      
      list.push({
        name: c.name || "",
        teacher: c.teacher || "",
        location: c.room || "",
        weekDay: c.dayName || '未知',
        timeSlot: "第" + c.period + "大节",
        weeks: weeksStr
      });
    }
    
    return JSON.stringify({
      success: true,
      schedule: list
    });
  } else {
    return JSON.stringify({
      success: false,
      error: "未找到课程数据"
    });
  }
}

// 转为纯文本格式
function formatAsPlainText(res) {
  if (!res || !res.courses || res.courses.length === 0) {
    return "错误: 未找到课程数据";
  }
  
  let text = "";
  
  for (let c of res.courses) {
    text += "课程:" + (c.name || "") + "\n";
    text += "教师:" + (c.teacher || "") + "\n";
    text += "地点:" + (c.room || "") + "\n";
    text += "星期:" + (c.dayName || "") + "\n";
    text += "节次:" + (c.period || 0) + "\n";
    text += "开始周:" + (c.weeks && c.weeks.length > 0 ? c.weeks[0] : 0) + "\n";
    text += "结束周:" + (c.weeks && c.weeks.length > 0 ? c.weeks[c.weeks.length - 1] : 0) + "\n";
    text += "开始节:" + (c.sections ? c.sections.s : 0) + "\n";
    text += "结束节:" + (c.sections ? c.sections.e : 0) + "\n";
    text += "---\n";
  }
  
  return text;
}

// 主函数 - 返回JSON
function extractSchedule() {
  let res = parse();
  return formatForAndroid(res);
}

// 返回纯文本
function extractScheduleAsText() {
  let res = parse();
  return formatAsPlainText(res);
}

// 导出函数
if (typeof window !== 'undefined') {
  window.extractSchedule = extractSchedule;
  window.extractScheduleAsText = extractScheduleAsText;
  window.parseSchedule = parse;
}
